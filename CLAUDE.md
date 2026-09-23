# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Marfeel Compass SDK for Android — a Kotlin Android library published as a Maven artifact (`com.marfeel.compass:views` / `com.marfeel.compass:compose`) to `repositories.mrf.io`. It instruments native Android apps for analytics tracking (pings, RFV), conversions, multimedia tracking, and an Experiences API for server-configured content (recirculation, recommenders, paywalls, experiments, etc.).

Module layout:
- `:compass` — the SDK library (`com.android.library`), the only artifact consumers install
- `:app` — sample/host application used for manual testing, depends on `:compass` via `project(":compass")`

## Build & Test

Gradle wrapper is checked in — use `./gradlew`.

The `:compass` library has two product flavors on a single `ui` dimension: `viewsUi` and `composeUi`. Release builds publish both as separate Maven artifacts (`views`, `compose`). Most library code is flavor-agnostic; the `composeUi` flavor only adds Compose runtime dependencies. The `:app` module pins `missingDimensionStrategy 'ui', 'composeUi'`.

```bash
./gradlew :compass:assembleViewsUiRelease          # build the views artifact
./gradlew :compass:assembleComposeUiRelease        # build the compose artifact
./gradlew :compass:testViewsUiDebugUnitTest        # run all unit tests (one flavor is enough for logic tests)
./gradlew :compass:testViewsUiDebugUnitTest --tests "com.marfeel.compass.experiences.ExperiencesTrackerTest"
./gradlew :compass:testViewsUiDebugUnitTest --tests "*.WholeModuleAugmenterTest.first_eligible_appends_sentinel"
./gradlew :compass:dokkaHtml                       # generate API docs into compass/build/dokka
./gradlew :compass:publish                         # publish to Nexus (requires JENKINS_PWD env var)
```

Unit tests use JUnit 4 + Robolectric + MockK + OkHttp MockWebServer. There is no lint task wired beyond Android's defaults.

Toolchain is pinned: AGP 7.4.2, Kotlin 1.8.0, JVM target 1.8, `compileSdk 33`, `minSdk 23`. Compose compiler extension 1.4.0. Don't bump these incidentally.

## Architecture

### Entry points (public API surface)

Three singletons accessed via `getInstance()`:

1. **`CompassTracking`** (`tracker/CompassTracker.kt`) — pageview/screen tracking, session vars, user identity, scroll tracking, conversions, RFV. Must be initialized first via `CompassTracking.initialize(context, accountId, tech)`.
2. **`Experiences`** (`experiences/Experiences.kt`) — fetches server-configured experiences for the currently-tracked page, with type/family filtering and optional content resolution. Also exposes lifecycle tracking methods (`trackEligible`, `trackImpression`, `trackClick`, `trackClose`).
3. **`Recirculation`** (`experiences/Recirculation.kt`) — lower-level recirculation event tracking keyed by module name.

`Experiences.trackImpression` bumps frequency-cap counters **and** delegates recirculation to `Recirculation`. `Experiences.trackClose` only bumps the close counter — it does not touch recirculation.

### Dependency wiring

`di/di.kt` holds a hand-rolled service locator (`CompassComponent : CompassServiceLocator`) — an `internal object` with lazy properties. No DI framework. The `Application` context is injected once in `CompassTracking.initialize` and is the root of every storage/SharedPreferences-backed component. When adding a new collaborator, wire it here and expose it on the `CompassServiceLocator` interface (the interface exists so tests can substitute the locator).

Two distinct `OkHttpClient` instances exist: `apiClient` for the core ingest/RFV pipeline, and `experiencesHttpClient` for experiences + recirculation + content resolution. Both add a `Marfeel-Android-SDK/<version> (Android) <deviceType>` `User-Agent` interceptor.

### Experiences pipeline

The Experiences feature is significantly more complex than the rest of the SDK. Read `docs/experiences-api.md` before non-trivial changes — it documents the wire format, frequency cap storage model, `uexp` encoding, ISO week semantics (Monday-first, minimal-days-in-first-week=4), experiment assignment, read-editorials delta encoding, and the whole-module augmentation rule.

Request flow: `ExperiencesApiClient` builds the request → server JSON → `ExperiencesResponseParser.parse` → `ParseResult(experiences, frequencyCapConfig, experimentGroups, editorialId)` → `ExperimentManager` assigns/filters variants → `FrequencyCapManager.applyResponseConfig` prunes stored counters → optional `ContentResolver.fetch` in parallel for `resolve=true`.

Key invariants that are easy to break:
- **Wire protocol spelling**: the server still expects the literal string `"elegible"` (sic) in recirculation payloads. The public method is `trackEligible` but **do not rename the wire string** when "fixing" typos.
- **Position serialization**: `RecirculationLink.position` is `Int` in the public API but must be serialized as a `String` (`"p": "0"`) in the recirculation POST payload. See `RecirculationApiClient`.
- **Whole-module augmentation**: `WholeModuleAugmenter` appends a synthetic `{url: " ", position: 255}` link on the first `trackEligible` for a module within a page lifecycle, and again on the first `trackImpression` if not yet impressed. `trackClick` is never augmented. State resets when the page URL changes.
- **Metadata keys**: the response parser must skip `targeting`, `content`, `experiments`, `experimentGroups` during type iteration — they are not experience types.
- **Family parsing**: `family` field present + known → enum case; present + unknown → `ExperienceFamily.UNKNOWN`; absent → `null`. These three cases are distinct and covered by `ExperiencesResponseParserTest`.
- **Bundled content URLs**: `ContentResolver` detects comma-separated ids in the `id` or nested `url` query param and fetches the bundle once, then slices per experience. Bundle state (including `varsReplayed`) is per-URL and mutex-protected.
- **OkHttp response leaks**: always wrap `execute()` with `.use { }`. `ContentResolver` already does this; match the pattern in any new HTTP code.

Persistence is SharedPreferences-based, one file per concern: `CompassExperiencesFreqCaps`, `CompassReadEditorials`, `CompassExperiments`. When iOS/Swift parity matters, these map to `UserDefaults` suites (see `docs/swift-implementation-guide.md`).

### Tracker / ping pipeline

`CompassTracker` owns the page lifecycle. `IngestPingEmitter` runs a timed ping loop while a page is tracked; `BackgroundWatcher` hooks `ProcessLifecycleOwner` so backgrounding resets the session if `> 30 min` have elapsed since the last ping. Scroll tracking attaches `OnScrollChangeListener` to `ScrollView` / `RecyclerView` / `FrameLayout+ScrollingView`; for non-native scroll containers (Flutter, Compose) consumers call `updateScrollPercentage(Int)` manually.

`storage/` holds both persistent (`Storage` — EncryptedSharedPreferences) and in-memory-per-session (`SessionStorage`) state. Several deprecated `startPageView` overloads are kept for binary compatibility — new code should use `trackNewPage` / `trackScreen`.

### CDP subsystem (`cdp/`)

A Customer Data Platform layer accessed via the **`Cdp`** singleton (`Cdp.getInstance()`): stable visitor `master_id`, read-only RFV/cohorts attached to beacons (`cdp_mid` / `cdp_rfv` / `cdp_cohorts` / `cdp_fresh`), host-pushed segments + properties, Server Segments / Server Properties mirrored from the CDP, server-authoritative meters, publisher consents, and the sign-out reset (`CompassTracking.resetUser()`). The web reference is `CompassTracker/src/cdp/` (`manager.js`, `unit.js`, `gateway.js`) plus `CompassTracker/new-cdp.md`; this module mirrors it. Key invariants:

- **Gated behind two independent conditions**: the `enableCdp` flag on `initialize` (stored in `SessionStorage`) **and** personalization consent (`storage.readUserConsent() != false`, so unknown allows). When either is off the identity subsystem is inert — no network. `CdpManager.hasConsent()` is the single **CMP** gate.
- **Publisher consents are a different "consent"**: `Cdp.trackConsent` / `getConsent` / `hasConsent` (manager: `trackCdpConsent` / `getCdpConsent` / `hasCdpConsent`) are gated only on `enableCdp`, **never** on CMP consent. Do not conflate with `hasConsent()`. Anonymous decisions are remembered in `CdpConsentMemoryStore` under the `local` bucket and replayed by the one-shot identity-resolved hook; the check is a POST so the subject never travels in a URL; `show_policy` folds to `ALWAYS` unless exactly `if-not-accepted`; `accept_method` passes through verbatim.
- **Strictly fail-open** — resolve/link/update resolve to `UNKNOWN_CDP_IDENTITY`; delete/reset/consent calls return `null` (they must never poison the cached identity). A CDP outage never breaks tracking.
- **Server Segments / Properties are never merged in storage**, only at read time (`SegmentOwnership.mergeSegments`, server first; `mergeVars`, device-owned wins). Beacon `useg` and `getUserSegments()` are the server-first union **trimmed to 100** (`MAX_SENT_SEGMENTS`), flagging the user var `mrf_tooManySegments` on the over/fits transitions only (`SegmentTrimmer`). Storage and CDP writes are never trimmed. `setUserSegments` rejects server-only keys (`rejectUnownedSegments`); `addUserSegment` is the way to claim ownership. `removeSegment`/`clearSegments` prune the server mirror eagerly. Server mirrors read `null` on a miss (distinct from empty) — the resolve guard skips the network only when both mirrors have an entry.
- **`identityFresh` / `cdp_fresh`**: true only after *this process* round-tripped a resolve or link that returned a master_id **and** mirrored its segments/properties; a warm cache never counts; a delete never sets it.
- **Native cache invalidation**: the resolve memo + one-shot latch are session-scoped (cleared on session rotation), cached rfv/cohorts are session-tagged in `Storage`, the meter mirror resets on a master_id change (`CdpManager.onMasterIdChanged`, wired in `di.kt`). `master_id` is the **only** intentionally-permanent cache (UUID-validated on read) — and `resetUser()` is the one thing that drops it.
- **`resetUser()`** (`tracker/UserResetter.kt` + `CompassTracker.rotateLocalUser`): the local rotation (`UserRotation.rotate`) is **synchronous in the caller's thread** before the first suspension, in this order: `CdpManager.clearIdentity()` **first** (it reads the live master_id to pick the mid-scoped buckets to wipe) → `Storage.resetUser()` (blanks user/visit fields incl. last-ping timestamps, keeps CMP consent) → re-mint user id + first visit → new session → re-point the running ping emitter → drop the RFV cache; the remote `POST /cdp/identity/reset/` is raced against 5 s and swallowed. No precondition, no identity re-resolve, concurrent calls share one run, never throws, not inferred from `setSiteUserId("")`. `clearIdentity` bumps a generation counter and `updateState` (the single write path) drops any response whose call started before it — resolve, link, delete, profile/segment updates and canonical-master adoption alike; `linkIdentity`/`deleteIdentity` pin the generation *before* their resolve so a reset that lands while they wait cancels them instead of re-identifying the new visitor. `Cdp.setIdentity` rejects an empty type or value (it would cache empty rfv/cohorts over the real ones). Cached rfv/cohorts are cleared to **absent**, not empty.
- **Public surface is add-only**: `CdpPublicSurfaceTest` pins every name on `Cdp` and the CDP-adjacent names on `CompassTracking`. Old flat names (`cdpDoIdentityLink`, `getCdpData`, `getCdpMasterId`) stay as deprecated delegates. `CdpIdentityTypes` deliberately omits `email_hash` / `phone_hash`.
- **Separate from legacy segments**: the CDP segment methods (`addCdpSegment` etc.) and the legacy `addUserSegment` family (`useg`) are independent stores — do not conflate. Likewise CDP RFV (`cdp_rfv`) is distinct from the legacy `rfv*` beacon fields.
- Mirror store (`CompassCdpMirror` prefs) is one file with prefixed keys per `(account, masterId)` — `cdpsegs_`, `cdpsrvsegs_`, `cdpsrvprops_`, `cdpmeters_`, `cdpconsents_` — 180-day TTL; the active master_id bucket is never purged.
- `setUserVar` pushes the device-owned vars to the CDP profile through a 50 ms debounced `updateProfile` (`CdpTracker.flushUserVars`); Server Properties are never echoed back.

## Gotchas

- `CompassTracking.initialize` is idempotent on subsequent calls (guarded by `CompassTracker.initialized`). The check reads `sessionStorage.readAccountId()`, so deleting the account id externally will re-enter initialization logic.
- `tech` argument to `initialize` is validated: it must be `> 100` or one of the `androidCorePageTypes` constants. Anything else throws.
- `fetchExperiences` reads the URL from the currently-tracked page (set by `trackNewPage`/`trackScreen`). If no page is active it returns an empty list rather than throwing — don't add a `check(initialized)` to the happy path.
- `resolve` defaults to **false** on `fetchExperiences`. Content is only fetched when explicitly requested or via `experience.resolve()`.
- Don't break public API shape without coordinating with the iOS SDK — `docs/swift-implementation-guide.md` is the cross-platform contract.

## Docs

- `docs/experiences-public-docs.md` — customer-facing docs for the Experiences / Recirculation API (matches the iOS public surface)
- `docs/experiences-api.md` — internal architecture reference (wire formats, frequency caps, experiments, recirculation payload)
- `docs/cdp-api.md` — internal architecture reference for the CDP subsystem (identity, segments, meters, gating, native lifecycle invalidation, beacon fields)
- `docs/swift-implementation-guide.md` — parity spec for the Swift port
