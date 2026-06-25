# CDP API — internal architecture reference (Android)

The Customer Data Platform (CDP) subsystem (`compass/src/main/java/com/marfeel/compass/cdp/`)
assigns a stable visitor **`master_id`**, carries read-only **RFV + cohorts**, lets the
host push **segments** and **properties**, and exposes server-authoritative **meters**.

It is strictly **fail-open**: a CDP outage must never break tracking.

## Gating — two independent conditions

The whole subsystem is inert unless **both** hold; otherwise every public method
no-ops / returns empty and **no network call** is made.

1. **`enableCdp`** — explicit opt-in on `CompassTracking.initialize(context, accountId, tech, enableCdp = false)`.
   Stored in `SessionStorage.cdpEnabled`.
2. **Personalization consent** — reuses the global consent: `storage.readUserConsent() != false`
   (so `true` *and* unknown/`null` allow; only an explicit `false` blocks).

`CdpManager.hasConsent()` combines both. The beacon gate (below) re-checks this on
every ping.

## Endpoints (`CdpConstants.kt`, host `BuildConfig.CDP_BASE_URL` = `https://events.newsroom.bi`)

| Purpose | Method | Path | Body / params |
|---|---|---|---|
| Resolve / mint | POST | `/cdp/identity/resolve/` | `CdpResolveParams` |
| Link identifier | POST | `/cdp/identity/link/` | `CdpLinkParams` |
| Update profile + segments | POST | `/cdp/identity/update/` | `CdpProfileUpdateParams` |
| Fetch all meters | GET | `/cdp/meters?site_id=&master_id=` | query string |
| Increment meter | POST | `/cdp/meters/{name}/increment?site_id=&master_id=` | null body |

- snake_case on the wire (Gson `@SerializedName`); trailing slashes on the identity
  paths are kept exactly. `site_id` / `master_id` / meter `name` are URL-encoded.
- **`site_id` is a JSON number** in the identity/profile POST bodies (`"site_id": 1234`)
  — a string `site_id` is rejected with 400. The params hold `siteId: Long` (parsed
  from the String `accountId`; non-numeric → fail-open, no call). The meters
  query-string `site_id=` is a string (query params always are).
- The three identity/profile POSTs return the same `CdpIdentityResponse`
  (`master_id` / `rfv` / `cohorts`) and **fail-open** to `UNKNOWN_CDP_IDENTITY`.
- Meters GET → `{ "meters": [...] }`; non-array `meters` → `[]`; error → `null`
  (SWR keeps last-good). Increment **404 → `MeterNotFoundError`**.

## Identity state machine (`CdpManager`)

- `resolveIdentity()` — memoized per session (`Deferred`, `Mutex`-guarded). Skips the
  network when a session-tagged cached identity **and** a master_id are both present;
  clears the memo on failure (no master_id) so the next call retries.
- `linkIdentity(type, value, isDeterministic)` — `resolveIdentity()` first, then POST
  link, then adopt whatever master_id comes back (backend merges may return a different
  one — that drives segment carry-over and a meter reset).
- `updateState` is the single write path: persist any returned master_id, **always**
  cache `{rfv, cohorts}` stamped with the current session, fire the one-shot if ready.
- `onIdentityResolved` — one-shot that fires when ready (enabled + consent + master_id);
  re-arms once per session and is re-checked on consent change. Used to reconcile
  segments, push `timezone` + user-vars, and seed/cleanup meters.

### Segments (local-first, `CdpSegmentsStore`)

Written **locally first** (even pre-consent, under the `"local"` bucket), synced via
`/cdp/identity/update/` only when consent + master_id are present. `replaceSegments`
diffs against the previous **local** snapshot. `reconcileSegments` pushes
`segments_add` only (idempotent). On a master_id change, `transferCdpSegments` unions
the previous bucket into the new one, clears the old, and sets the active mid.

The legacy `addUserSegment` family (the `useg` beacon field + experiences `useg` query
param) is **separate and untouched** — CDP uses `addCdpSegment` / `setCdpSegments` /
`removeCdpSegment` / `clearCdpSegments` / `getCdpSegments`.

### Meters — stale-while-revalidate (`MeteredCounter`, `CdpMetersStore`)

Sync `getMeter` / `listMeters` read an in-memory mirror seeded from persistence.
`getMeterSnapshot()` refreshes once per page (dedup via an inflight `Deferred`),
fail-open. `invalidate()` is called on every `trackNewPage` / `trackScreen`.
`increment(name)` upserts on success, throws `MeterNotFoundError` on 404, and falls
open to the mirror value otherwise. `reset()` clears the mirror on a master_id change.

## Persistence & TTL

- `master_id` — `Storage` (`cdpMasterId_key`), **UUID-validated on read** (non-UUID →
  treated as absent → fresh resolve). Permanent by design; the only intentional
  "forever" cache.
- cached `rfv` / `cohorts` — `Storage`, **session-tagged** (`cdpCacheSessionId_key`);
  read as absent once the session rotates.
- mirror store (`CompassCdpMirror` prefs) — one file, prefixed keys per
  `(account, masterId)`, **180-day TTL** (`CDP_MIRROR_TTL_MS`, matches Scylla
  `DefaultAnonymousTTL`); the active master_id bucket is never purged.

## Native lifecycle invalidation (the "cached forever" problem)

The web gets cache invalidation free (every navigation destroys the JS context). On a
process-lifetime native singleton it doesn't, so invalidation is driven by the two
events the SDK already emits:

1. **Session rotation** (≤30 min inactivity) clears the resolve memo + one-shot latch
   (`memoSessionId`) and the session-tagged rfv/cohorts cache → fresh resolve.
2. **master_id change** resets the meter in-memory mirror (`MeteredCounter.reset()` via
   `CdpManager.onMasterIdChanged`, wired in `di.kt`).
3. **Session start re-resolves (revalidate the master_id).** `CompassTracker.configureSession`
   fires `Cdp.onSessionStart()` whenever a new session begins — cold start and the
   30-min rotation (which covers app foreground via `onResume`). Since the memo and
   rfv/cohorts cache are session-scoped, the new session is a cache miss → the resolve
   **re-sends the existing master_id** and refreshes rfv/cohorts. The id is kept (the
   backend may still return a merged/different one, handled by the normal change path).
   A re-open within the same session window does not re-request. The one-shot is
   registered before `configureSession` in `initialize` so it can't be missed.

No background timers. `master_id` is the only intentionally-permanent cache.

## Beacon fields (`IngestPingData` / `IngestPing`)

`cdp_mid` / `cdp_rfv` / `cdp_cohorts` are appended **only when personalization consent
is present AND a master_id exists** (mirrors web `if (consent) { if (masterId) {...} }`).
`cdp_rfv` / `cdp_cohorts` carry the JSON-string serialized forms. Gson omits null
members, so a disabled CDP adds nothing. These are **distinct** from the legacy
`useg` / `rfv*` fields — do not conflate CDP RFV with the legacy RFV.
