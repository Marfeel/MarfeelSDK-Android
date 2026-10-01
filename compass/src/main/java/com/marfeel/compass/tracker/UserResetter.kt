package com.marfeel.compass.tracker

import com.marfeel.compass.cdp.REMOTE_CLEANUP_TIMEOUT_MS
import com.marfeel.compass.storage.SessionStorage
import com.marfeel.compass.storage.Storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The synchronous half of `resetUser()`: turns the persisted user into a brand-new
 * visitor through the SDK's **real** first-visit path rather than a re-implementation.
 *
 * Order matters:
 *  1. The local CDP wipe goes **first**: it reads the live master_id to pick the
 *     mid-scoped buckets (segments, server mirrors, meters) it has to clear, and
 *     [Storage.resetUser] would have deleted that id already. It never re-resolves.
 *  2. [Storage.resetUser] blanks every user- and visit-scoped field, including the
 *     last-ping timestamps — they must go **before** the new session is minted, or
 *     `SessionStorage.updateSession` would carry the previous user's last ping into `lv`.
 *  3. The new internal user id and first-visit timestamp are minted eagerly, so a
 *     beacon fired right after already carries them.
 *  4. A new session is minted (this also clears session vars and the landing page) and
 *     the running page is re-pointed at it so its remaining beacons carry the new `s`.
 *  5. Session-scoped caches that outlive storage go: the memoised RFV.
 */
internal class UserRotation(
	private val storage: Storage,
	private val sessionStorage: SessionStorage,
	private val updateEmitterSession: (sessionId: String) -> Unit,
	private val clearRfvCache: () -> Unit,
	private val clearCdpIdentity: () -> Unit
) {
	fun rotate() {
		clearCdpIdentity()
		storage.resetUser()
		storage.readOriginalUserId()
		storage.readFirstSessionTimeStamp()
		sessionStorage.updateSession()
		updateEmitterSession(sessionStorage.readSession().id)
		clearRfvCache()
	}
}

/**
 * Makes this device a new visitor. Called by hand on sign-out — deliberately **not**
 * inferred from `setSiteUserId("")`, which integrations send on every anonymous
 * pageview.
 *
 * Load-bearing decisions (each from a real sign-out; do not "simplify" them away):
 *
 * 1. **Rotate locally before calling the server.** A beacon can fire while the remote
 *    tail is in flight; one carrying the *old* user id would re-create the identity the
 *    reset is deleting. Rotating first means such a beacon carries the new id.
 * 2. **Rotation is synchronous**, in the caller's thread, before the first suspension —
 *    callers get it whether or not they await.
 * 3. **No precondition.** A user identified only via `Cdp.setIdentity` never has a site
 *    user id; every step is idempotent, so the only cost of a duplicate run is rotating
 *    an already-anonymous visitor.
 * 4. **An in-flight run is returned, not skipped**, so a second caller awaits the same
 *    reset instead of getting an early resolve and navigating away.
 * 5. **No identity re-resolve** in here — the next `trackNewPage` does that; minting a
 *    master here would orphan one on every duplicate reset.
 * 6. **The remote tail is raced against a timer and never rejects.** A failure
 *    propagating out would skip the caller's own sign-out step — worse than an
 *    incomplete remote reset.
 */
internal class UserResetter(
	private val rotateLocalUser: () -> Unit,
	private val clearRemoteState: suspend () -> Unit,
	private val remoteTimeoutMs: Long = REMOTE_CLEANUP_TIMEOUT_MS,
	private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
	private val lock = Any()
	private var inFlight: Deferred<Unit>? = null

	/**
	 * Runs the synchronous local rotation (unless a reset is already in flight) and
	 * returns the run to await. Never throws.
	 */
	fun start(): Deferred<Unit> = synchronized(lock) {
		inFlight ?: run {
			try {
				rotateLocalUser()
			} catch (_: Exception) {
				// the remote tail still runs; a partial rotation beats no rotation
			}
			scope.async { remoteTail() }.also { deferred ->
				inFlight = deferred
				deferred.invokeOnCompletion {
					synchronized(lock) { if (inFlight === deferred) inFlight = null }
				}
			}
		}
	}

	/** Awaits the shared run. Always settles; never throws. */
	suspend fun reset() {
		try {
			start().await()
		} catch (_: Exception) {
		}
	}

	private suspend fun remoteTail() {
		withTimeoutOrNull(remoteTimeoutMs) {
			try {
				clearRemoteState()
			} catch (_: Exception) {
				// swallowed: the request may still land after the caller moved on
			}
		}
	}
}
