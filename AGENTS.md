# Repository Guidelines

## Project Structure & Module Organization
- Root Gradle scripts (`settings.gradle`, `build.gradle`, `gradle.properties`) configure the Android library and sample app.
- `compass/` hosts the Compass SDK: runtime code in `compass/src/main/java`, Compose assets under its product flavors, and shared resources in `compass/src/main/res`.
- `app/` is a sample host that exercises the SDK; its sources live in `app/src/main/java` with instrumentation scaffolding in `app/src/androidTest`.
- Unit tests are concentrated in `compass/src/test/java`; instrumentation suites sit in `compass/src/androidTest/java`.

## Build, Test, and Development Commands
- `./gradlew assembleRelease` builds the SDK artifacts for the default flavor set.
- `./gradlew compass:assembleViewsUiRelease` / `./gradlew compass:assembleComposeUiRelease` produce flavor-specific AARs.
- `./gradlew test` runs the JVM unit suite; append `compass:test` for module-only execution.
- `./gradlew connectedAndroidTest` executes instrumentation tests against an attached emulator or device.
- `./gradlew compass:dokkaHtml` renders API docs to `compass/build/dokka`.

## Coding Style & Naming Conventions
- Kotlin sources follow the official style guide: 4-space indentation, braces on the same line, and trailing commas where it improves diffs.
- Use PascalCase for classes, camelCase for members and functions, and UPPER_SNAKE_CASE for shared constants. Package names remain lowercase dotted paths.
- Extension files and test doubles should mirror their production counterparts (e.g., `session/SessionStorage.kt` with `SessionStorageTest.kt`).

## Testing Guidelines
- Write focused JUnit 4 tests with MockK or MockWebServer to isolate HTTP and storage behavior.
- Instrumentation cases rely on AndroidX Test and Espresso; name them `<Feature>InstrumentedTest` and guard them with `@LargeTest` when they touch network mocks.
- Ensure new features include unit coverage plus instrumentation only when SDK/device interaction is required. Capture emulator API level and command output (`./gradlew connectedAndroidTest`) in PR checks.

## Commit & Pull Request Guidelines
- Follow the existing Conventional Commit style (`feat: ...`, `fix: ...`) and append the PR reference in parentheses when applicable.
- Keep commits scoped to a single concern and update docs or changelog entries alongside code.
- PR descriptions should state the user-facing intent, list validation commands run, link to Jira or community threads, and attach logs or screenshots for UI-impacting changes.

## Publishing & Credentials
- Maven publishing uses the `maven-publish` plugin in `compass/build.gradle`; required credentials (`JENKINS_PWD`) must be injected at runtime, never committed.
- Validate flavor-specific artifacts locally before requesting a release pipeline execution.
