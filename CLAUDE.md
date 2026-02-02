# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Compass SDK is an Android library for integrating digital media with Marfeel's Compass technology. It provides page tracking, scroll percentage monitoring, user identification, RFV (Recency, Frequency, Value) data retrieval, and conversion tracking.

## Build Commands

```bash
# Build all variants
./gradlew build

# Build specific UI flavors
./gradlew assembleViewsUi      # Traditional Android Views
./gradlew assembleComposeUi    # Jetpack Compose

# Run unit tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.marfeel.compass.memory.MemoryTest"

# Run instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest

# Install demo app
./gradlew installDebug

# Generate documentation
./gradlew dokkaHtml
```

## Module Structure

- **compass/**: Main SDK library module
- **app/**: Demo application showing integration examples

## Build Flavors

The `compass` module has a `ui` flavor dimension:
- `viewsUi`: Traditional Android Views implementation
- `composeUi`: Jetpack Compose implementation (Compose is only enabled for this flavor)

Compose-specific code lives in `compass/src/composeUi/`.

## Architecture

The SDK follows clean architecture with these layers:

```
tracker/CompassTracker.kt     # Public API - singleton entry point (CompassTracking interface)
    ↓
usecase/                      # Business logic (Ping, GetRFV use cases)
    ↓
memory/Memory.kt              # In-memory session state (account, page, conversions)
storage/Storage.kt            # Persistent encrypted storage (user IDs, timestamps)
    ↓
network/ApiClient.kt          # OkHttp-based HTTP client for Compass endpoints
```

**Key architectural patterns:**
- Koin for dependency injection (defined in `di/di.kt`)
- Coroutines for async operations (`Dispatchers.IO`)
- `PingEmitter` sends tracking pings every 10 seconds, pauses when app is in background
- `BackgroundWatcher` monitors app lifecycle via `ProcessLifecycleOwner`
- `EncryptedSharedPreferences` for secure persistent storage

## Key Files

| File | Purpose |
|------|---------|
| `compass/tracker/CompassTracker.kt` | Main SDK entry point |
| `compass/core/PingEmitter.kt` | Manages periodic ping emission |
| `compass/memory/Memory.kt` | Session-level volatile state |
| `compass/storage/Storage.kt` | Encrypted persistent storage |
| `compass/network/ApiClient.kt` | HTTP requests to Compass endpoints |
| `compass/di/di.kt` | Koin dependency injection setup |

## Testing

- **JUnit 4** for unit tests
- **MockK** for mocking
- **Robolectric** for Android-dependent unit tests
- **OkHttp MockWebServer** for network tests

Test files are in `compass/src/test/java/`. Key test fixtures include `FakeKeyStore.kt` for encryption tests.

## SDK Configuration

Build config fields in `compass/build.gradle`:
- `COMPASS_PING_BASE_URL`: "https://events.newsroom.bi"
- `COMPASS_RFV_BASE_URL`: "https://compassdata.mrf.io"
- `VERSION`: SDK version string

## SDK Usage Pattern

```kotlin
// Initialize in Application.onCreate()
CompassTracking.initialize(context, "accountId")

// Get tracker instance
val tracker = CompassTracking.getInstance()

// Start page tracking
tracker.startPageView("https://example.com")

// For scroll tracking with Views
tracker.startPageView(url, nestedScrollView)

// For scroll tracking with Compose
CompassScrollTrackerEffect(scrollState)

// Set user identification
tracker.setUserId("user123")
tracker.setUserType(UserType.Logged)

// Get RFV data
tracker.getRFV { rfv -> /* handle rfv */ }

// Track conversion
tracker.track(conversion)

// Stop tracking
tracker.stopTracking()
```
