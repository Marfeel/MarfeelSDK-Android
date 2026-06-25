# CDP Implementation Plan — Marfeel Compass SDK (Android)

> Source of truth: `CompassTracker/src/cdp/implementation-reviewed.md` (the
> reviewed, line-referenced port spec) + the JS reference implementation in
> `CompassTracker/src/cdp/`. This plan maps that spec onto the Android SDK's
> existing conventions (service locator in `di/di.kt`, `Storage` /
> `SessionStorage`, OkHttp clients, JUnit4 + Robolectric + MockK + MockWebServer).

---

## 0. What we're building

A **Customer Data Platform (CDP)** subsystem that:

1. Assigns/resolves a stable **`master_id`** (UUID) per visitor against the backend.
2. Carries **RFV** + **cohorts** (backend → SDK, read-only) and attaches them to
   ingest beacons as `cdp_mid` / `cdp_rfv` / `cdp_cohorts`.
3. Lets the host app push **properties** (key/value) and **segments** (labels) to
   the backend, with a local-first segment mirror.
4. Exposes **meters** (server-authoritative paywall counters) with
   stale-while-revalidate caching.
5. Is gated behind a per-site **`hasCdp`** flag **and** **personalization consent**,
   and is strictly **fail-open** (a CDP outage must never break tracking).

Everything is **new code** — `grep` confirms no CDP subsystem exists yet. We keep
the existing `addUserSegment` / `setUserSegments` / `removeUserSegment` /
`clearUserSegments` legacy segment store (used for the `useg` beacon field +
experiences `useg` query param) untouched, and add the CDP segment store alongside
it (see §9 for the reconciliation decision).

---

## 1. Web → Android mapping (the contract)

| Web concept | Android equivalent | Notes |
|---|---|---|
| `endpointDomain` | **new** `BuildConfig.CDP_BASE_URL` → `https://events.newsroom.bi` | Dedicated field so it can be repointed independently (§2). |
| `site_id` / `accountId` | `sessionStorage.readAccountId()` | String. |
| `cookie_id` | `storage.readOriginalUserId()` | The device/anonymous UUID the tracker already mints. |
| consent for `"personalization"` | `storage.readUserConsent() != false` | Reuse global consent (§3.2). |
| `hasCdp` per-site flag | **new** `enableCdp` flag on `initialize` | Explicit host-app opt-in (§3.1). |
| permanent cookie `cdpMasterId` | **new** `Storage` key, UUID-validated on read | §4. |
| session cookie `cdpRfv`/`cdpCohorts` | **new** in-memory + persisted cache in `Storage` | §4. |
| localStorage mirror (segments/meters) | **new** SharedPreferences-backed `CdpMirrorStore` | one prefs file, per-`(account,masterId)` keying. §5. |
| `post`/`get` from common lib | **new** `CdpApiClient` (OkHttp), reuses the experiences `User-Agent` client | §6. |
| async Promises + memo | Kotlin coroutines + `Mutex`/`Deferred` | §7. |
| `setSiteUserId(userId)` (login) | triggers `linkIdentity("registered_user_id", userId, isDeterministic=true)` | §8. |

---

## 2. Endpoints (replicate exactly — keep trailing slashes)

Add to `compass/build.gradle` `defaultConfig`:

```groovy
buildConfigField "String", "CDP_BASE_URL", "\"https://events.newsroom.bi\""
```

Paths (constants in a new `cdp/CdpConstants.kt`):

| Purpose | Method | Path | Body / params |
|---|---|---|---|
| Resolve / mint | POST | `/cdp/identity/resolve/` | JSON `CdpResolveParams` |
| Link identifier | POST | `/cdp/identity/link/` | JSON `CdpLinkParams` |
| Update profile + segments | POST | `/cdp/identity/update/` | JSON `CdpProfileUpdateParams` |
| Fetch all meters | GET | `/cdp/meters?site_id=&master_id=` | query string |
| Increment meter | POST | `/cdp/meters/{name}/increment?site_id=&master_id=` | null body, `application/json` |

- All three identity/profile POSTs return the **same shape** `CdpIdentityResponse`
  (`master_id` / `rfv` / `cohorts`).
- snake_case on the wire — keep exact. URL-encode `site_id`, `master_id`, and the
  meter `name`.
- **`site_id` is a JSON _number_ in the identity/profile POST bodies** (`"site_id": 1234`),
  not a string — the backend rejects a string `site_id` with **400**. The SDK's
  `accountId` is a `String`, so the params parse it to `Long` (`toLongOrNull`); a
  non-numeric account id aborts the CDP call (fail-open). The meters query-string
  `site_id=` is a string regardless (query params are strings). See §19 decision 7.
- Meters GET → `{ "meters": [...] }`; non-array `meters` → `[]`.
- Increment **404 → `MeterNotFoundError`** (distinct from generic failure).

**Endpoint host — DECIDED:** `CDP_BASE_URL` defaults to `events.newsroom.bi` (the
ingest host the tracker already uses, per the web spec's "same ingest host"). It is
isolated behind its own `BuildConfig` field so it can be repointed independently if
backend confirms a different host — a one-line change. See §19.

---

## 3. Gating — `hasCdp` + consent (two independent conditions)

The whole subsystem is inert unless **both** hold. All public methods no-op /
return null-empty when disabled, and **no network call** happens.

### 3.1 `hasCdp` (per-site feature flag) — **DECIDED: explicit init flag**

Web reads it from page config (`window.__mrfCompass`). Android has no remote config
wired today, so the host app opts in explicitly:

```kotlin
fun initialize(context, accountId, tech = androidPageType, enableCdp: Boolean = false)
```

The `enableCdp` boolean is stored (`sessionStorage`/`Storage`) and flows into
`CdpManager` / `MeteredCounter` as the JS `enabled` option. When `false`, the entire
subsystem is inert: every public method no-ops, `getData` returns
`(null, null, [])`, and no network call is ever made.

### 3.2 Consent (`"personalization"`) — **DECIDED: reuse global consent**

Web: `hasConsent = enabled && getConsent("personalization")`, default `true` with a
5s CMP timeout.

Android has a **single global** consent (`storage.readUserConsent()`, `Boolean?`
where `null` = unknown). The CDP reuses it via one helper in `CdpManager`:

```kotlin
fun hasConsent(): Boolean = enabled && storage.readUserConsent() != false
```

i.e. `true` **and** `null`/unknown both allow (matching web's default-true), only an
explicit `false` blocks — consistent with how recirculation already treats consent.

### 3.3 Consent-change re-resolve

There is **no consent observer today** — `Storage.updateUserConsent` just toggles
prefs. Add a lightweight listener hook:
- In `CompassTracker.setUserConsent`, after `storage.updateUserConsent(...)`, call
  `Cdp.onConsentChanged()` which (a) re-runs `resolveIdentity` and (b) fires the
  `onIdentityResolved` one-shot if now ready (reconcile segments + push properties).

---

## 4. Persisted identity state (`Storage` additions)

Add to `storage/Storage.kt` (these live in the **persistent**/`MockSharedPreference`
toggled prefs, same as other keys, so they respect the existing consent toggle):

| State | Key | Type | Lifetime | Rule |
|---|---|---|---|---|
| `master_id` | `cdpMasterId_key` | String | long-lived (**permanent by design**) | **Validated as UUID on read** — non-UUID → return `null` (behaves as absent → triggers fresh resolve). |
| cached `rfv` | `cdpRfv_key` | String (JSON) | **session-scoped** | last response cache (see §13). |
| cached `cohorts` | `cdpCohorts_key` | String (JSON) | **session-scoped** | **read returns null if not a JSON array** (corrupt-cache guard). |
| cache session tag | `cdpCacheSessionId_key` | String | — | the session id the rfv/cohorts cache belongs to. |

New `Storage` methods:
- `readCdpMasterId(): String?` — read + `isValidUuid` guard (use
  `runCatching { UUID.fromString(it) }` or a regex; mirror `isValidUuid`).
- `writeCdpMasterId(newId: String): String?` — persist, **return the old value**
  (needed for segment carry-over, §9.2).
- `readCdpCachedIdentity(currentSessionId: String): CdpCachedIdentity?` — returns
  null if both rfv+cohorts absent, null if cohorts isn't an array (corrupt-cache
  guard), **and null if the stored cache session tag ≠ `currentSessionId`** (stale
  from a previous session — see §13). Defensive parse.
- `writeCdpCachedIdentity(rfv: CdpRfv?, cohorts: List<Int>, currentSessionId: String)`
  — stamps the session tag.

> **Why session-scoped, not "forever":** the web caches rfv/cohorts in a ~30-min
> sliding session cookie, so they naturally expire. Android prefs have no TTL — if we
> just persisted them, a long-lived process would serve stale rfv/cohorts forever.
> Tagging the cache with the SDK's session id makes `getCachedIdentity` treat the
> cache as absent once the session rotates, forcing a fresh resolve. `master_id`
> stays permanent (it is identity, not a value-cache). See §13 for the full model.

---

## 5. Mirror store (segments + meters) — `cdp/store/`

Port `cdpMirrorStore.js` to a generic SharedPreferences-backed store. **One prefs
file** `CompassCdpMirror` (one file per concern is the repo convention — but a single
file with prefixed keys mirrors the JS design and is simpler; either is fine. Plan
uses **one file, prefixed keys**, matching JS).

`cdp/store/CdpMirrorStore.kt`:
```kotlin
internal class CdpMirrorStore<T>(
    private val prefs: SharedPreferences,
    private val prefix: String,        // "cdpsegs_" | "cdpmeters_"
    private val payloadKey: String,     // "segments" | "meters"
    private val serialize: (T) -> String,
    private val deserialize: (String) -> T,
    private val default: T,
    private val clock: () -> Long = System::currentTimeMillis,
)
```
Key families (per `accountId`):
- data: `${prefix}${masterId}_${accountId}` → `{ "<payloadKey>": <json>, "ts": <ms> }`
- index: `${prefix}index_${accountId}` → JSON `List<String>` of masterIds
- active mid: `${prefix}active_mid_${accountId}` → String

Behaviors to replicate exactly:
- `read`: no-op (→ default) when account/masterId falsy; return payload **only if**
  envelope is a fresh object with numeric `ts` and `now - ts < TTL`. Stale/malformed
  → default.
- `write`: stamp `ts`, add masterId to index. No-op on falsy account/masterId.
- `clear(account, masterId)`: null out data + remove from index.
- `getActiveMid` / `setActiveMid`.
- `cleanupExpired(account, activeMidOverride?)`: purge index entries older than TTL
  or missing, **never purge the active masterId**, rewrite index to survivors.
- `TTL = 180L * 24 * 60 * 60 * 1000` (`CDP_MIRROR_TTL_MS`). Comment: matches Scylla
  `DefaultAnonymousTTL`.

`cdp/store/CdpSegmentsStore.kt` — wraps `CdpMirrorStore<List<String>>` with
`prefix="cdpsegs_"`, `payloadKey="segments"`. Export `LOCAL_MID_SENTINEL = "local"`.

`cdp/store/CdpMetersStore.kt` — wraps `CdpMirrorStore<List<MeterState>>` with
`prefix="cdpmeters_"`, `payloadKey="meters"`, **serializing `Date` as ISO-8601
strings** (`startedAt`/`expiresAt`), preserving the optional `threshold/reached/
remaining` trio and the `window` default `{duration:"",period:"",tz:""}`.

> SharedPreferences is synchronous, so these stores are **synchronous** (no
> coroutines needed inside). Network is the only async part.

---

## 6. Models + API client — `cdp/model/` + `cdp/CdpApiClient.kt`

### 6.1 Models (`cdp/model/`)
- `CdpRfv(rfv: Int, r: Int, f: Int, v: Int)`
- Param bodies (`CdpResolveParams` / `CdpLinkParams` / `CdpProfileUpdateParams`) carry
  **`siteId: Long`** so `site_id` serializes as a JSON number (§2).
- `CdpIdentityResponse(master_id: String?, rfv: CdpRfv?, cohorts: List<Int>)` —
  Gson `@SerializedName` for snake_case; `cohorts` defaults to `[]`.
- `CdpCachedIdentity(rfv: CdpRfv?, cohorts: List<Int>)`
- `MeterWindow(duration: String, period: String, tz: String)`
- `MeterState(name, count: Int = 0, threshold: Int?, reached: Boolean?, remaining: Int?, startedAt: Date?, expiresAt: Date?, window: MeterWindow)`
- `CdpData(masterId: String?, rfv: CdpRfv?, cohorts: List<Int>)` (+ serialized form).
- `MeterNotFoundError(name: String) : Exception`.

`UNKNOWN_CDP_IDENTITY = CdpIdentityResponse(null, null, emptyList())`.

### 6.2 `CdpApiClient`
Constructor takes the **experiences-style OkHttpClient** (the one with the
`Marfeel-Android-SDK/<version>` User-Agent interceptor) + `baseUrl =
BuildConfig.CDP_BASE_URL`. All methods are `suspend` (wrap blocking OkHttp in
`withContext(Dispatchers.IO)`), each wrapped `.use { }`.

Identity/profile — **fail-open** to `UNKNOWN_CDP_IDENTITY` on any error/non-2xx/parse
failure (never throw):
- `suspend fun resolve(params): CdpIdentityResponse`
- `suspend fun link(params): CdpIdentityResponse`
- `suspend fun update(params): CdpIdentityResponse`

Meters:
- `suspend fun fetchMeters(siteId, masterId): List<MeterState>?` — null on
  error/parse-fail (so SWR keeps last-good); `[]` when `meters` not an array.
- `suspend fun incrementMeter(name, siteId, masterId): IncrementResult` where the
  result carries `status` so the caller can map **404 → MeterNotFoundError**.

Use Gson with custom parsing for `MeterState` (the optional threshold trio + date
parsing) — mirror `parseMeterState`.

---

## 7. `CdpManager` — identity state machine (`cdp/CdpManager.kt`)

Port `manager.js`. Constructor params (wired via DI, §11):
`accountId provider`, `api: CdpApiClient`, `enabled: Boolean`, plus storage hooks
`getMasterId()`, `writeMasterId(new): old`, `getUserId()`, `getCachedIdentity()`,
`setCachedIdentity()`, and a `hasConsent()` predicate, and `segmentsStore`.

State: `identityResolution: Deferred<Unit>?` (memo), `identityResolved: Boolean`,
`onIdentityResolved` callback. Guard concurrency with a `Mutex` around the memo
assignment.

Methods (all `suspend` where they hit network):
- `hasConsent()` → `enabled && <consent rule from §3.2>`.
- `getStorageMid()` → `getMasterId() ?: LOCAL_MID_SENTINEL`.
- `initialize()` — if enabled and not yet consented, register consent-change → resolve.
- `onIdentityResolved(callback)` — one-shot, fires when `enabled && consent &&
  masterId`. Check immediately on register + on every consent change; latch
  `identityResolved`. Covers fresh resolve / returning visitor (synchronous) /
  consent-granted-later.
- `resolveIdentity()`:
  1. gate on consent → return.
  2. **session-scope the memo (native-specific, see §13):** read the current session
     id; if it differs from `memoSessionId`, set `identityResolution = null` and
     `memoSessionId = currentSession`. This is what replaces the web's
     "fresh-instance-per-page" reset — without it the memo would latch for the whole
     process lifetime.
  3. memoize: `identityResolution ??= async { runResolve() }`; concurrent callers
     await the same Deferred.
  4. skip if `getCachedIdentity(currentSession) != null && getMasterId() != null`
     (the cached-identity read is itself session-scoped, §4).
  5. POST resolve with `{site_id, cookie_id, master_id?}`.
  6. `updateState(result)` — caches rfv/cohorts stamped with `currentSession`.
  7. if still no masterId after apply → **clear memo** (retry next call).
- `linkIdentity(type, value, isDeterministic)`:
  1. gate. 2. `resolveIdentity()` first. 3. POST link. 4. `updateState` — adopt
  whatever master_id comes back (backend may merge → different id; that drives
  carry-over §9.2).
- `updateState(result)` — single write path: falsy → no-op; truthy `master_id` →
  `writeMasterId`; **always** cache `{rfv, cohorts}`; fire one-shot if ready.
- `updateProfile(properties: List<Pair<String,String>>)` — gate on consent **and**
  master_id (no local buffering — no-op without master_id). Stringify values. POST
  `properties` object. Funnel through `updateState`.
- Segments: `addSegment` / `removeSegment` / `replaceSegments` / `clearSegments` /
  `reconcileSegments` / `postSegmentChange` — see §9.
- `getData(serialized: Boolean): CdpData` — disabled → `(null, null, [])`. Else read
  masterId + cached rfv/cohorts. Serialized form: rfv→JSON-string-or-`""`,
  cohorts→JSON string, wrapped in try/catch fallback `("", "[]")`.

---

## 8. `linkIdentity` wiring — the login path

`setSiteUserId(userId)` is the Android "user logged in" path. After
`storage.updateUserId(userId)`, call:
```kotlin
Cdp.linkIdentity("registered_user_id", userId, isDeterministic = true)
```
(launched on the IO scope; fail-open). **Do not rename the wire string** — backend
canonical login id is `registered_user_id` (not in the TS sample enum, by design).
Public passthrough method `cdpDoIdentityLink(type, value, isDeterministic = false)`
exposes the generic form (see §10).

---

## 9. Segments (local-first, with carry-over)

### 9.1 Operations (in `CdpManager`)
- `addSegment(s)`: read local for `(account, storageMid)`; if present → no-op; else
  write `local + s` immediately; then `postSegmentChange(segments_add=[s])`.
- `removeSegment(s)`: write local minus s; then `postSegmentChange(segments_remove=[s])`.
- `clearSegments()`: write `[]`; if previous non-empty → `postSegmentChange(segments_remove=previous)`.
- `replaceSegments(list)`: dedup; diff vs **previous local snapshot**; write; if any
  add/remove → POST the delta.
- All **no-op when `!enabled`**, but **write locally even without consent/master_id**
  (so pre-consent segments survive). `storageMid = masterId ?: "local"`.
- `postSegmentChange(body)`: **no-op without consent or master_id**; else POST to
  `/cdp/identity/update/` → `updateState`.
- `reconcileSegments()`: require consent + master_id; read local for the **real**
  master_id; if non-empty POST all as `segments_add` (adds only — pre-consent removes
  not recovered; idempotent on backend).

### 9.2 Master_id carry-over (`transferCdpSegments`)
Hook this into `writeMasterId`: when DI builds the manager, wrap the storage
`writeCdpMasterId` so that after the write (which returns the old id) it launches
`transferCdpSegments(account, oldId, newId)` (best-effort, swallow errors):
1. `previousMid = oldId ?: segmentsStore.getActiveMid(account) ?: LOCAL_MID_SENTINEL`.
2. if `previousMid == newId` → return.
3. union previous bucket's segments into new bucket.
4. clear previous bucket (data + index).
5. set active mid = newId.
6. `cleanupExpired`.

### 9.3 Relationship to the legacy segment store — **DECIDED: separate methods**
Android already has `storage.readUserSegments()` (legacy) feeding the `useg` beacon
field + experiences `useg` query param, with public methods `addUserSegment` etc.
The web SDK dual-writes for backward compat, but the spec says **a fresh native SDK
should keep a single CDP mirror**.

We add **CDP-specific segment methods** on the new `Cdp` surface
(`addCdpSegment` / `setCdpSegments` / `removeCdpSegment` / `clearCdpSegments` /
`getCdpSegments`) and leave the existing `addUserSegment` family **untouched**
(legacy `useg` keeps working exactly as today). Cleanest separation, zero behaviour
change to existing integrations. Skip the web legacy-migration
(`cdpSegmentsMigration.js`) entirely — Android has no `usegs` legacy to merge.

> Coordinate the public names with the iOS parity owner so docs stay consistent.

---

## 10. Meters — `cdp/MeteredCounter.kt`

Port `metered.js`. Holds `cdpManager` (for `accountId`, `getMasterId`, `hasConsent`)
+ `metersStore` + `api`. State: in-memory `meters: List<MeterState>`, `fresh: Boolean`,
`inflight: Deferred?` (dedup), `seeded: Deferred?` (idempotent seed). Guard with a
`Mutex`.

- `ready()` = consent + master_id.
- `seed()` — idempotent (shared Deferred); gated on ready; only when mirror empty
  before+after the store read; fill from `metersStore` so sync `get`/`list` return
  last-known before any network. Best-effort.
- `getMeterSnapshot()` — not ready → return current mirror; fresh → return mirror
  (shared); stale → fetch once (dedup via `inflight`), on success replace mirror,
  mark fresh, persist; fail-open (keep last-good, stay not-fresh → retry next call).
- `invalidate()` — flip `fresh=false` (cheap). **Call on every `trackNewPage`/
  `trackScreen`** (mirrors web invalidating on every page-init).
- `get(name)` / `list()` — **sync** reads of the mirror.
- `increment(name)` — gate ready→null; POST; **404 → throw `MeterNotFoundError`**;
  on success upsert into mirror + persist + return; other failure → return current
  mirror value (or null).
- `cleanupExpiredMeters(account, activeMid)` — opportunistic, run on the
  onIdentityResolved one-shot (alongside seed).
- **`reset()` on master_id change (native-specific, §13):** when `writeMasterId`
  adopts a different id (fresh resolve or backend merge), clear the in-memory meter
  mirror + `fresh` + `seeded`/`inflight` so we don't surface the *old* identity's
  counts and the next snapshot re-seeds/re-fetches for the new master_id. The
  carry-over hook (§9.2) is the natural place to also fire this meter reset.

---

## 11. DI wiring (`di/di.kt`)

Add to `CompassServiceLocator` + `CompassComponent`:
- `cdpApiClient: CdpApiClient` (lazy) — built with `experiencesHttpClient` (reuse the
  existing User-Agent client) + `BuildConfig.CDP_BASE_URL`.
- `cdpMirrorPrefs` — `context.getSharedPreferences("CompassCdpMirror", MODE_PRIVATE)`.
- `cdpSegmentsStore`, `cdpMetersStore` (lazy, from `cdpMirrorPrefs`).
- `cdpManager: CdpManager` (lazy) — wired with `storage`/`sessionStorage` hooks:
  - `getMasterId = storage::readCdpMasterId`
  - `writeMasterId = { storage.writeCdpMasterId(it) }` (returns old; carry-over wrapped here)
  - `getUserId = storage::readOriginalUserId`
  - `getCachedIdentity = storage::readCdpCachedIdentity`
  - `setCachedIdentity = storage::writeCdpCachedIdentity`
  - `accountId = { sessionStorage.readAccountId() }`
  - `enabled` flag from §3.1.
- `meteredCounter: MeteredCounter` (lazy).

Follow the existing pattern: `checkNotNull(context)` for prefs-backed components.

---

## 12. Public surface

### 12.1 `Cdp` singleton (mirror `Experiences.kt` style) — `cdp/Cdp.kt`
```kotlin
interface Cdp {
    fun cdpDoIdentityLink(type: String, value: String, isDeterministic: Boolean = false)
    fun getCdpData(serialized: Boolean = false): CdpData
    fun getCdpMasterId(): String?
    // segments (see §9.3 decision)
    fun addCdpSegment(s: String); fun removeCdpSegment(s: String)
    fun setCdpSegments(segments: List<String>); fun clearCdpSegments()
    fun getCdpSegments(): List<String>            // sync, reads CDP mirror
    // meters
    suspend fun getMeterSnapshot(): List<MeterState>
    fun getMeter(name: String): MeterState?       // sync
    fun listMeters(): List<MeterState>            // sync
    suspend fun incrementMeter(name: String): MeterState?   // throws MeterNotFoundError
    companion object { fun getInstance(): Cdp = CdpTracker }
}
```
Internal `object CdpTracker : Cdp` pulls collaborators from `CompassComponent`,
launches fire-and-forget work on a `CoroutineScope(Dispatchers.IO)` like
`ExperiencesTracker`. Async-from-sync passthroughs (link, segment ops) launch on the
scope; `getMeterSnapshot`/`incrementMeter` are `suspend`.

### 12.2 Hooks into `CompassTracker`
- `initialize(...)`: after session config, call `Cdp` init + register the
  `onIdentityResolved` one-shot work (reconcile segments + push `timezone` property +
  seed/cleanup meters). Push `timezone` via
  `TimeZone.getDefault().id` (IANA). Skip web-only `facebook_user_id` / `gam_ppid`.
- `trackNewPage` / `trackScreen` (the single private `trackNewPage(url, rs)` choke
  point): call `Cdp.onNewPage()` → `resolveIdentity()` (launched) + `invalidateMeters()`.
- **`configureSession` (session start):** when a new session is created (cold start or
  the 30-min-inactivity rotation, including the `onResume` path), call
  `Cdp.onSessionStart()` → `resolveIdentity()` + `invalidateMeters()`. This
  **revalidates the master_id on every session start** ("app open") without waiting for
  the next page track. The one-shot registration is moved **before** `configureSession`
  in `initialize` so the callback is armed before the first session-start resolve can
  complete and latch. See §13.2 rule 3 and §19 decision 6.
- `setSiteUserId`: trigger `linkIdentity("registered_user_id", id, true)` (§8).
- `setUserConsent`: after toggling, call `Cdp.onConsentChanged()` (§3.3).
- `setUserVar`: **DECIDED — push on the one-shot only** (v1). On the
  `onIdentityResolved` one-shot, push the current user vars + `timezone` via
  `updateProfile`. The web's 50ms-debounced live push of every `setUserVar` is
  deferred to v2 (not needed for parity correctness).

### 12.3 Beacon fields (`IngestPingData` + `IngestPing`)
Append `cdp_mid` / `cdp_rfv` / `cdp_cohorts` to the ingest payload **only when
personalization consent is present AND master_id exists** (both conditions, mirror
the web `if (consent) { if (masterId) {...} }`).
- Add nullable fields to `IngestPingData` (`@SerializedName("cdp_mid")` etc.) +
  extend `copy(...)`.
- In `IngestPing.getData`, read `CdpComponent.cdpManager.getData(serialized=true)`
  and populate the three fields under the gate; leave null/absent otherwise so Gson
  omits them. Keep the existing legacy `useg` and the old `rfv*` fields separate —
  **do not conflate** CDP RFV with the legacy RFV.

> Verify the `FormBody` `addJson` path (ApiClient §162-170) skips null/absent JSON
> members so disabled CDP adds nothing. Gson's tree omits nulls by default with the
> serializer in use — confirm in the IngestPingDataSerializer.

---

## 13. Native lifecycle & cache invalidation (the "cached forever" problem)

**Why this section exists.** The web CDP gets cache invalidation *for free*: every
navigation reloads the page, destroying the JS context. So the resolve-memo, the
`onIdentityResolved` latch, the cached rfv/cohorts (session cookie), and the meter
`fresh` flag all reset on every page load — the publisher never thinks about it.

A native SDK is the opposite: `CompassComponent`, `CdpManager` and `MeteredCounter`
are **process-lifetime singletons**. A phone can keep the app warm for days. So any
in-memory latch or untimed cache would **stick forever** unless we invalidate it
deliberately. The anchor we use for "a unit of activity" is the SDK's **existing
session** (`Session(id, timeStamp)`), which already rotates after 30 min of
inactivity (`configureSession()` at init + `pingEmitter.onResumeCallback`, and
`BackgroundWatcher`). The session id is our native stand-in for the web's
"page-load boundary."

### 13.1 What is cached, and exactly what invalidates it

| Cache | Where | Invalidation trigger | Can it live forever? |
|---|---|---|---|
| `master_id` | `Storage` (persistent) | backend merge on link; UUID-validate on read | **Yes — by design.** It's identity, not a value-cache. The only "reset" is the backend returning a different/merged id, or a corrupt non-UUID read (→ treated as absent → re-resolve). This is correct and matches web's permanent cookie. |
| cached `rfv` / `cohorts` | `Storage`, **session-tagged** | session rotation (tag mismatch → read as absent) | **No.** Refreshes every session (≤30 min of inactivity). |
| `resolveIdentity` memo (`Deferred`) | `CdpManager` in-memory | session rotation (`memoSessionId` mismatch → cleared); resolve failure (no master_id → cleared); **also actively re-triggered at session start** by `configureSession`→`onSessionStart` (§13.2 rule 3) | **No.** Re-resolves once per session, retries on failure, revalidates on every new session. |
| `onIdentityResolved` latch | `CdpManager` in-memory | fires once per session (reset alongside the memo); also re-checked on consent change | **No.** Re-fires reconcile/properties once per new session and on consent grant. |
| meter mirror + `fresh` | `MeteredCounter` in-memory | `invalidate()` on **every** `trackNewPage`/`trackScreen` (page-grained, tighter than session); `reset()` on master_id change | **No.** Re-fetched on the next snapshot after any page change. |
| meter `seeded` | `MeteredCounter` in-memory | `reset()` on master_id change | Effectively once per identity (fine — it only seeds the mirror from storage). |
| segments / meters **persisted mirror** | `CompassCdpMirror` prefs | 180-day TTL + `cleanupExpired`; **active master_id bucket never purged** | **No** for idle users (purged at 180d, matching backend anonymous TTL). The *active* user's bucket persists as long as they're active — which is correct, not a leak. |

### 13.2 The two invalidation rules to implement

1. **Session-scope the in-memory identity state.** `CdpManager` records the session
   id its memo/latch belong to (`memoSessionId`). At the top of `resolveIdentity`
   (and when registering / checking the one-shot), if
   `sessionStorage.readSession().id != memoSessionId`, clear `identityResolution`,
   reset the `identityResolved` latch, and adopt the new id. The cached-identity read
   is likewise session-tagged (§4), so the skip-if-known check naturally fails in a
   new session and forces a fresh resolve. **Net effect:** identity + rfv/cohorts
   refresh about once per 30-min activity window, exactly like the web session cookie
   — no explicit timer needed, we piggyback the session the SDK already rotates.

2. **Reset meter in-memory state on master_id change.** When `writeMasterId` adopts a
   different id (fresh resolve or backend merge), the carry-over hook (§9.2) also
   calls `meteredCounter.reset()` so we never surface the previous identity's counts;
   the next `getMeterSnapshot` re-seeds + re-fetches for the new master_id. (Meters
   are server-authoritative per master_id, so unlike segments they are **not** carried
   over — just discarded and re-fetched.)

3. **Revalidate the master_id on every session start (DECIDED — for apps).** The web
   gets this free (page reload), but a native app can stay warm for days. So
   `CompassTracker.configureSession` fires `Cdp.onSessionStart()` whenever a new session
   begins (cold start + the 30-min-inactivity rotation, which also covers app
   foreground via `onResume`). Because the resolve memo and the cached rfv/cohorts are
   already session-scoped (rule 1), a new session is a guaranteed cache miss → the
   resolve hits the network and **re-sends the existing master_id** (revalidate +
   refresh rfv/cohorts). The permanent master_id is **kept, not discarded** — the
   backend may still return a different/merged id, which flows through the normal
   change path (carry-over + meter reset). Net effect: "open the app" ⇒ re-request the
   id. Note: a re-open *within* the same session window (no rotation) does **not**
   re-request — that's the same session, rfv/cohorts are still fresh.

> No background timers, no `WorkManager`. Invalidation is driven entirely by the two
> events the SDK already emits — **session rotation** and **page change** — plus the
> existing 180-day storage cleanup. The single intentional "forever" is `master_id`,
> which is identity and *should* persist.

---

## 14. Concurrency & threading notes

- SharedPreferences I/O is synchronous → mirror stores are plain synchronous classes.
- Network = coroutines on `Dispatchers.IO`. `CdpApiClient` methods are `suspend`.
- `resolveIdentity` memo = `Deferred<Unit>?` guarded by a `Mutex`; concurrent
  callers `await` the same Deferred. On failure (no master_id after apply) set memo
  to null under the mutex.
- `MeteredCounter` `inflight`/`seeded` Deferreds guarded by a `Mutex`.
- Public sync getters (`getMeter`, `getCdpMasterId`, `getCdpData`) just read storage
  / in-memory mirror — no suspension (no web "gateway mirror" indirection needed; the
  whole §12 web-only lazy gateway/unit split is dropped per spec §12).

---

## 15. Tests (JUnit4 + Robolectric + MockK + MockWebServer)

Mirror the JS test files; run under `viewsUi` flavor
(`./gradlew :compass:testViewsUiDebugUnitTest`). Use a real
`SharedPreferences` from Robolectric `ApplicationProvider` for store tests, and
`MockWebServer` for the API client.

- `CdpMirrorStoreTest` — TTL freshness, falsy-guard no-ops, index add/remove,
  cleanup never purges active mid, malformed envelope → default.
- `CdpSegmentsStoreTest` / `CdpMetersStoreTest` — date ISO round-trip, optional
  threshold trio preserved, missing window default.
- `CdpApiClientTest` (MockWebServer) — fail-open to UNKNOWN on non-2xx/garbage;
  meters non-array → `[]`; increment 404 → MeterNotFoundError; trailing slashes;
  snake_case bodies; query encoding.
- `CdpManagerTest` — resolve memoize/skip-if-known/retry-on-fail; cached-without-mid
  re-resolves; link awaits resolve then adopts returned id; updateState single
  write-path caches rfv/cohorts even w/o mid; updateProfile stringify + no-op w/o
  mid; segments local-first + diff-vs-previous-local; reconcile adds-only; carry-over
  union+clear+active-mid; onIdentityResolved one-shot fires once across the three
  arrival paths.
- `MeteredCounterTest` — SWR seed/fetch-once/dedup/persist/fail-open; invalidate;
  increment upsert + 404.
- `StorageCdpTest` — masterId UUID-validated on read; cached-identity null-guards
  (undefined + non-array cohorts); writeMasterId returns old id.
- Beacon test — `cdp_*` present only under consent + master_id; absent otherwise;
  legacy `useg`/`rfv*` unaffected.
- **`CdpLifecycleTest`** (§13) — session rotation clears the resolve memo +
  re-resolves; session-tagged cached identity reads as absent in a new session;
  master_id change resets the meter mirror; `master_id` itself survives session
  rotation (persists).

---

## 16. Invariants checklist (port-review aid — from spec §13)

- [ ] All identity/profile calls fail-open to `{master_id:null, rfv:null, cohorts:[]}`.
- [ ] `resolveIdentity` runs at most once per session; failure clears memo to retry.
- [ ] Cached rfv/cohorts **without** master_id ≠ resolved → re-resolve.
- [ ] No network without `hasCdp` AND personalization consent.
- [ ] `linkIdentity` awaits `resolveIdentity`, then adopts returned master_id (may differ).
- [ ] Segments written **locally first**, synced when consent + master_id.
- [ ] Pre-identity segments use the `"local"` bucket and carry over on first resolve.
- [ ] Master_id change unions old bucket into new, clears old (best-effort, swallow).
- [ ] Reconcile pushes `segments_add` only.
- [ ] `replaceSegments` diffs vs previous **local** list.
- [ ] Property values stringified; `updateProfile` no-ops without master_id.
- [ ] Meters SWR: seed → fetch once per page (dedup) → persist → fail-open.
- [ ] Meter increment 404 → `MeterNotFoundError`; other errors → mirror value.
- [ ] Mirror TTL 180d; active master_id never purged; falsy account/mid → no-op.
- [ ] `master_id` UUID-validated **on read** — non-UUID behaves as absent.
- [ ] Cached-identity read null if cohorts isn't an array.
- [ ] `cdp_mid`/`cdp_rfv`/`cdp_cohorts` on beacon **only** when consented AND
      master_id present; legacy `rfv*`/`useg` are separate fields.
- [ ] **(native)** resolve memo + one-shot latch are session-scoped (cleared on
      session rotation); cached rfv/cohorts are session-tagged; meter mirror reset on
      master_id change; `master_id` is the only intentionally-permanent cache.
- [ ] **(native)** every session start (cold start + 30-min rotation / app foreground)
      actively re-resolves to revalidate the master_id; same-session re-opens do not.
- [ ] `site_id` is a JSON **number** in identity/profile POST bodies (string → 400).

---

## 17. File map (new code)

```
compass/src/main/java/com/marfeel/compass/cdp/
  Cdp.kt                       # public interface + CdpTracker object
  CdpManager.kt                # identity state machine, segments, properties
  MeteredCounter.kt            # meters SWR + MeterNotFoundError
  CdpApiClient.kt              # resolve/link/update + meters HTTP (fail-open)
  CdpConstants.kt              # paths, UNKNOWN_CDP_IDENTITY, TTL, LOCAL_MID_SENTINEL
  model/
    CdpModels.kt               # CdpRfv, CdpIdentityResponse, CdpCachedIdentity,
                               #   CdpData, params types
    MeterState.kt              # MeterState, MeterWindow, MeterNotFoundError
  store/
    CdpMirrorStore.kt          # generic per-(account,masterId) TTL store
    CdpSegmentsStore.kt
    CdpMetersStore.kt
```
Edits to existing files:
- `compass/build.gradle` — `CDP_BASE_URL` buildConfigField.
- `di/di.kt` — wire cdpApiClient, stores, manager, meteredCounter.
- `storage/Storage.kt` — masterId (UUID-validated) + cached rfv/cohorts.
- `tracker/CompassTracker.kt` — init one-shot, trackNewPage resolve+invalidate,
  setSiteUserId link, setUserConsent re-resolve, (optional) setUserVar push.
- `core/model/compass/IngestPingData.kt` + `usecase/IngestPing.kt` — beacon fields.

Tests under `compass/src/test/java/com/marfeel/compass/cdp/`.

---

## 18. Phasing (suggested PR breakdown)

1. **Foundation** — models, constants, `CdpApiClient` (+ MockWebServer tests),
   `Storage` identity additions (+ tests), `build.gradle` field. No behaviour change.
2. **Stores** — `CdpMirrorStore` + segments/meters stores (+ tests).
3. **Manager + lifecycle** — `CdpManager` identity/properties/segments + carry-over,
   plus the **session-scoped memo/latch + session-tagged cached identity** (§13)
   (+ tests). Wire DI, `resolveIdentity` on trackNewPage, `linkIdentity` on
   setSiteUserId, consent re-resolve, onIdentityResolved one-shot.
4. **Meters** — `MeteredCounter` (+ tests), `invalidate()` on page change,
   `reset()` on master_id change (§13).
5. **Public surface + beacons** — `Cdp` singleton, beacon `cdp_*` fields (+ tests).
6. **Docs** — add `docs/cdp-api.md` (internal reference) and update CLAUDE.md
   Architecture section; coordinate the public surface with the iOS parity owner.

---

## 19. Decisions

**Locked in:**
1. **`hasCdp` gating** — explicit `enableCdp` flag on `initialize` (§3.1).
2. **Consent purpose** — reuse the global consent, `!= false` allows (§3.2).
3. **Segment surface** — separate CDP segment methods; legacy `addUserSegment`
   family untouched (§9.3).
4. **User-var push** — on the `onIdentityResolved` one-shot only; live debounce
   deferred to v2 (§12.2).
5. **CDP host** — `BuildConfig.CDP_BASE_URL` **defaulting to `events.newsroom.bi`**
   (the ingest host), isolated so it can be repointed without touching code (§2).
6. **Session-start revalidation (apps)** — every new SDK session re-resolves the
   master_id (revalidate + refresh rfv/cohorts), keeping the id. Triggered from
   `configureSession`→`Cdp.onSessionStart()`; piggybacks the existing 30-min session
   rotation, no timers (§12.2, §13.2 rule 3).
7. **`site_id` as a number** — identity/profile POST bodies serialize `site_id` as a
   JSON number (params hold `siteId: Long`), because the backend rejects a string
   `site_id` with 400. Non-numeric account ids fail-open (no CDP call) (§2, §6.1).

**One thing to confirm with backend (non-blocking):** that `events.newsroom.bi` is in
fact the CDP host for native. If it later turns out to differ, it's a one-line
`build.gradle` change thanks to the dedicated field. Phase 1 can start now.
