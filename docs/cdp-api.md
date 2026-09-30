# CDP subsystem — internal reference

Architecture reference for the Customer Data Platform layer in `compass/src/main/java/com/marfeel/compass/cdp/`. It is a port of the web `CompassTracker` (`src/cdp/manager.js`, `unit.js`, `gateway.js`, spec in `CompassTracker/new-cdp.md`). When the two disagree, the web is the source of truth unless a native deviation is listed under [Native deviations](#native-deviations).

## Layers

| Layer | File | Role |
|---|---|---|
| Public facade | `Cdp.kt` (`Cdp` interface, `CdpTracker` object) | Add-only public surface; fire-and-forget wrappers; lifecycle hooks called from `CompassTracker` |
| State machine | `CdpManager.kt` | Identity, mirrors, segments, consents, generation guard. All gating lives here |
| Transport | `CdpApiClient.kt` | One method per endpoint. Never throws |
| Meters | `MeteredCounter.kt` | Server-authoritative meters, in-memory mirror + `CdpMetersStore` |
| Read-side views | `UserDataMerger.kt`, `SegmentOwnership.kt` | Merge device-owned + server data for `useg` / `uvar` / getters |
| Reset | `tracker/UserResetter.kt` | `resetUser()` rotation + remote tail |
| Wiring | `di/di.kt` | Builds `CdpManager` with storage lambdas; `onMasterIdChanged` / `onIdentityCleared` reset meters |

## Gating

Two independent gates — do not conflate them.

- **CDP enabled**: `initialize(..., enableCdp = true)`, stored in `SessionStorage.readCdpEnabled()`. Off → everything is inert (no network, `null` / `false` / empty answers).
- **CMP (personalization) consent**: `CdpManager.hasConsent()` = enabled **and** `storage.readUserConsent() != false` (unknown allows). Gates resolve, link, delete, update, segment sync and meters.
- **Publisher consents** (`trackConsent` / `getConsent` / `hasConsent` on `Cdp`) are gated **only** on CDP enabled, never on CMP consent.

## Endpoints

Base URL: `BuildConfig.CDP_BASE_URL`. HTTP client: the shared `experiencesHttpClient` (SDK `User-Agent` interceptor).

| Call | Path | Failure value |
|---|---|---|
| resolve | `POST /cdp/identity/resolve/` | `UNKNOWN_CDP_IDENTITY` |
| link | `POST /cdp/identity/link/` | `UNKNOWN_CDP_IDENTITY` |
| update (properties / segments) | `POST /cdp/identity/update/` | `UNKNOWN_CDP_IDENTITY` |
| delete | `POST /cdp/identity/delete/` | `null` |
| reset | `POST /cdp/identity/reset/` (body `{site_id}` only) | `null` |
| consent record | `POST /cdp/consents/record/` | `null` |
| consent catalog | `GET /cdp/consents/catalog/?site_id&consent_id&consent_version_id?` | `null` |
| consent check | `POST /cdp/consents/check/` (POST so the subject never travels in a URL) | `null` |
| meters | `GET /cdp/meters`, `POST /cdp/meters/{name}/increment` | `null` / error result |

Identity responses share one shape: `master_id`, `rfv`, `cohorts`, optional `segments` (Server Segments) and `properties` (Server Properties). `delete` adds `deleted`; `reset` answers `{reset, site_id, cleared}` (on native there are no cookies, so `cleared` is empty — parity plumbing).

**Fail-open rule**: calls whose success feeds the cached identity but whose failure must not poison it (delete, reset, consents) return `null`. resolve / link / update return `UNKNOWN_CDP_IDENTITY` (`master_id = null`, `rfv = null`, `cohorts = []`).

## Identity

- `master_id` lives in `Storage` (EncryptedSharedPreferences), UUID-validated on read. It is the only intentionally permanent cache; only `resetUser()` drops it.
- Cached rfv/cohorts are **session-tagged**: a read with a different session id returns `null`.
- `resolveIdentity()` is memoised per `(session, generation)` behind a mutex; concurrent callers share one `Deferred`. A resolve with no resulting `master_id` clears the memo so the next call retries.
- **Warm path**: cached identity for this session + `master_id` + both server mirrors present (non-`null`) → no network, mirrors loaded into memory.
- Triggers: `trackNewPage` / `trackScreen` (`CdpTracker.onNewPage`), session start, CMP consent change, `setSiteUserId` (links `registered_user_id`, deterministic).
- `setSiteUserId("")` still stores the empty id exactly as before; the CDP link is skipped for an empty value (web links only a truthy site user id). `CdpManager.linkIdentity` drops any empty type/value, which also covers the deprecated `cdpDoIdentityLink`. The public `Cdp.setIdentity` throws `IllegalArgumentException` instead.
- `linkIdentity` / `deleteIdentity` resolve first, then act on the resulting master.
- **One-shot identity-resolved hook** (`registerOnIdentityResolved`): once there is consent + a master, merge legacy `useg` into the CDP segment store, reconcile segments (`segments_add`), push user vars + `timezone`, seed meters, replay anonymous consent decisions. Re-arms after `clearIdentity()`.

### `updateState` and the generation guard

`updateState(result, session, startGeneration)` is the **single write path** for identity responses:

1. Drop the response if `generation != startGeneration` (a `clearIdentity()` happened since the call started).
2. If `master_id` is present: write it, carry segments over from the previous bucket, drop the previous master's server mirrors, fire `onMasterIdChanged` if it changed.
3. Write the cached rfv/cohorts for the session and try the one-shot hook.

`linkIdentity` / `deleteIdentity` pin the generation **before** their inner resolve, so a reset landing while they wait cancels them instead of re-identifying the new visitor.

`/update/` responses without a `master_id` are ignored entirely (`postUpdate`): update is only sent with a master and a successful answer always echoes one, so a missing one means the call failed. Without this a failed `setUserVar` push would blank the cached rfv/cohorts for the rest of the session. resolve keeps web behaviour (caches rfv/cohorts even without a master).

### `identityFresh` → `cdp_fresh`

True only after **this process** round-tripped a resolve or link that returned a `master_id` and mirrored its segments/properties. A warm cache never sets it; delete never sets it; `clearIdentity()` clears it.

## Segments and properties

There are three distinct stores — keep them apart:

| Store | Owner | Where |
|---|---|---|
| Legacy user segments (`useg`) | device | `Storage.readUserSegments()` |
| CDP segments | device | `CdpSegmentsStore` (`cdpsegs_`), per `(account, masterId)`; `local` bucket before a master exists |
| Server Segments | CDP | `CdpServerSegmentsStore` (`cdpsrvsegs_`) + in-memory `CdpManager.serverSegments` |
| Server Properties | CDP | `CdpServerPropertiesStore` (`cdpsrvprops_`) + in-memory `CdpManager.serverProperties` |

Server mirrors store only keys the device does **not** own (filtered in `syncServerSegments`). A mirror miss reads `null`, distinct from an empty list/map; the warm-path guard depends on that.

### Read-time merge (never in storage)

- **Segments** (`useg`, `getUserSegments()`, `getUserSegmentsAsync()`): `SegmentOwnership.mergeSegments` = Server Segments first, then device-owned, deduplicated, then `SegmentTrimmer` caps at **100** (`MAX_SENT_SEGMENTS`). Server-first means device-owned segments are the ones dropped. Storage and CDP writes are never trimmed. With CDP disabled the server side is empty and the cap still applies.
- **`mrf_tooManySegments`**: set to `"true"` when the union goes over 100, removed when it fits again — only on those transitions, so it is cheap on every beacon. It is written as a device-owned user var and therefore also pushed to the CDP profile; `mrf_*` vars are internal and never shown to clients.
- **Vars** (`uvar`, `getUserVars()`, `getUserVarsAsync()`): `SegmentOwnership.mergeVars` = device-owned first, then Server Properties whose key the device does not own. Device-owned wins. **Not trimmed** (same as web).
- The `*Async` getters resolve identity first; the sync getters use whatever is in memory.

### Write rules

- `setUserSegments(list)` drops keys that only the server asserts (`rejectUnownedSegments`, logs a warning): an echo of `getUserSegments()` must not claim server segments, or a later `clearUserSegments` would send them in `segments_remove`. `addUserSegment` is the explicit way to claim one.
- `removeUserSegment` / `clearUserSegments` also prune the in-memory + stored Server Segment mirror immediately.
- `setUserVar` persists, then pushes **device-owned vars only** to the CDP through a 50 ms debounced `updateProfile` (`CdpTracker.flushUserVars`). Server Properties are never echoed back.
- `Storage` serialises user var / segment read-modify-write behind `userDataLock` (the ping thread writes the flag).

## Publisher consents

- `trackConsent(CdpConsent)`: best-effort resolve, then `POST record` under the current master. `email` is hashed on-device (`email_sha256`, falling back to normalised `email`) and sent as the subject.
- **Canonical-master adoption**: if the record response returns a different, valid UUID `master_id` than the one sent, it is adopted through `updateState` (keeping the cached rfv/cohorts).
- **Anonymous decisions** (no master when recorded) are remembered in `CdpConsentMemoryStore` (`cdpconsents_`, always the `local` bucket) and replayed by the one-shot hook once a master exists; successfully replayed ids are forgotten.
- `hasConsent(query)`: resolve first; with neither master nor email it answers from local memory; otherwise `POST check`. `false` on any failure — never `null`.
- `getConsent(ref)`: catalog read, no resolve. `show_policy` folds to `ALWAYS` unless exactly `if-not-accepted`; `accept_method` passes through verbatim.

## Meters

`MeteredCounter`: in-memory mirror, stale-while-revalidate on `getMeterSnapshot()`, invalidated on each page and session start, seeded by the one-shot hook. Ready only with consent + master. Reset on a master_id change (`onMasterIdChanged`) and on `clearIdentity()` (`onIdentityCleared` also clears the previous master's `cdpmeters_` bucket).

## `resetUser()`

`CompassTracking.resetUser()` (suspend) / `resetUser(onComplete)` / deprecated `resetIdentity()`. Called by the host on sign-out; **not** inferred from `setSiteUserId("")`.

1. `UserResetter.start()` runs `UserRotation.rotate()` **synchronously in the caller's thread**, before any suspension:
   1. `CdpManager.clearIdentity()` — **first**, because it reads the live master_id to pick the buckets to wipe. Bumps the generation, clears master_id, cached rfv/cohorts (to **absent**, not empty), resolve memo, `identityFresh`, one-shot latch, in-memory mirrors, the previous master's `cdpsegs_` / `cdpsrvsegs_` / `cdpsrvprops_` buckets, anonymous consent memory, resets the active mid to `local`, and fires `onIdentityCleared` (meters).
   2. `Storage.resetUser()` — removes site user id, original user id, user type, first visit, last-ping timestamps, user vars, user segments, session, session vars, landing page and the CDP identity keys. **Keeps CMP consent** (it belongs to the device).
   3. Mint a new user id and first-visit timestamp.
   4. `SessionStorage.updateSession()` → new session; the running ping emitter is re-pointed at it (`IngestPingEmitter.updateSessionId`). The page id is unchanged — call `trackNewPage` / `trackScreen` after a sign-out that stays on screen.
   5. Drop the memoised RFV (`GetRFV.clearCache`).
2. Remote tail: `POST /cdp/identity/reset/`, raced against 5 s (`REMOTE_CLEANUP_TIMEOUT_MS`), errors swallowed.
3. Concurrent calls share the in-flight run. No precondition, never throws, never re-resolves identity (the next page does).
4. The callback form fires `onComplete` on the **main thread** once the remote tail settles.

## Public surface

`Cdp` is **add-only**, pinned by `CdpPublicSurfaceTest` together with the CDP-adjacent names on `CompassTracking`. Superseded names stay as deprecated delegates:

| Deprecated | Replacement |
|---|---|
| `cdpDoIdentityLink` | `setIdentity` |
| `getCdpData` | `getUserProfile` |
| `getCdpMasterId` | `getMasterId` |
| `CompassTracking.resetIdentity` | `resetUser` |

`CdpIdentityTypes` documents the well-known types (stable vs device-bound) and deliberately omits `email_hash` / `phone_hash`: the server does not validate them and they would create a parallel user that never merges with `*_sha256`. Use `Cdp.hashEmail` / `hashPhone` (normalise per `cdp-core/pkg/identity/normalize.go`: email `trim + lowercase`, phone `trim` only) and send `email_sha256` / `phone_sha256`.

## Beacon fields

Added by `IngestPing` only when `hasConsent()` and a master exist:

| Field | Value |
|---|---|
| `cdp_mid` | master_id |
| `cdp_rfv` | JSON-serialised cached rfv (`""` when none) |
| `cdp_cohorts` | JSON-serialised cohorts |
| `cdp_fresh` | `"1"` when `identityFresh`, absent otherwise |

`useg` / `uvar` always come from the merged views above (`CompassTracker.getUserSegments()` / `getUserVars()`). They are distinct from the legacy `rfv*` beacon fields.

## Persistence

`CompassCdpMirror` SharedPreferences, one file, keys prefixed per `(account, masterId)`: `cdpsegs_`, `cdpsrvsegs_`, `cdpsrvprops_`, `cdpmeters_`, `cdpconsents_`. Entries are TTL-stamped at 180 days (`CDP_MIRROR_TTL_MS`, matches the backend anonymous TTL); the active master_id bucket is never purged. Maps to `UserDefaults` suites on iOS.

## Native deviations

Where Android intentionally differs from the web:

- `/update/` responses without a `master_id` are ignored instead of overwriting the cached rfv/cohorts (web caches the fail-open shape).
- `resetUser(onComplete)` has no web equivalent; its callback runs on the main thread.
- The reset endpoint has no cookies to clear on native; it is called for parity only.
