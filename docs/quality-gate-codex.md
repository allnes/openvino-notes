# Quality Gate Codex

## Required CI Gates

Every pull request runs the full CI stack:

- `Preflight`
- `Dependency Review`
- `Build Quality (x86_64)`
- `Build Quality (arm64)`
- `Secrets`
- `CodeQL`
- `Release`
- `Android Tests`

The CI entrypoint is:

- `.github/workflows/ci.yml`

Execution is delegated into reusable workflow files:

- `.github/workflows/preflight.yml`
- `.github/workflows/quality.yml`
- `.github/workflows/supply-chain.yml`
- `.github/workflows/security.yml`
- `.github/workflows/codeql.yml`
- `.github/workflows/release.yml`
- `.github/workflows/android-tests.yml`

Shared Android/Java/Gradle bootstrap lives in:

- `.github/actions/setup-android-gradle/action.yml`

`main`, `merge_group`, scheduled runs, and manual runs use the same workflow shape so required checks stay stable.

The execution graph is staged:

- `Preflight` starts first
- `Dependency Review`, `Secrets`, and `Build Quality` branch from `Preflight`
- `CodeQL` waits for `Build Quality` and `Secrets`
- `Release` waits for `Build Quality`
- `Android Tests` wait for `Build Quality`

Inside the reusable workflows, the expensive work is also staged:

- `Preflight`: `Detect Changes` -> `Preflight`
- `Quality`: `Quality Foundation` -> parallel `Build Quality (x86_64)`, `Build Quality (arm64)`, and `Coverage`
- `Release`: `Release Assemble` -> `Release Lint`
- `Android Tests`: `Emulator Validation` -> 4-way matrix `(build host x emulator host)`

## Scope

The CI pipeline enforces these repository rules:

- Kotlin formatting and style with `ktlintCheck`
- Kotlin static analysis with `detekt`
- Android-specific analysis with `lintDebug` and `lintRelease`
- JVM unit tests across all modules
- Coverage verification with `koverVerify`
- Debug and release build integrity
- Secret scanning with `gitleaks`
- SAST with `CodeQL`
- Dependency change review on pull requests
- Cross-matrix Android instrumentation execution for `(x86 build, arm build) x (x86 emulator, arm emulator)`
- Native build checks on both `x86_64` and `arm64` hosts

## Local Commands

Use these commands before opening a pull request:

```bash
./gradlew ktlintFormat
./gradlew ktlintCheck detekt
./gradlew ai:lintDebug app:lintDebug data:lintDebug domain:lintDebug
./gradlew ai:testDebugUnitTest app:testDebugUnitTest data:testDebugUnitTest domain:testDebugUnitTest
./gradlew ai:assembleDebug app:assembleDebug data:assembleDebug domain:assembleDebug
./gradlew koverXmlReport koverVerify
```

If you touch release behavior or Android UI flows, also run:

```bash
./gradlew ai:assembleRelease app:assembleRelease data:assembleRelease domain:assembleRelease
./gradlew ai:lintRelease app:lintRelease data:lintRelease domain:lintRelease
./gradlew app:assembleDebug app:assembleDebugAndroidTest
```

## Runner Policy

- `Build Quality (x86_64)` runs on `ubuntu-24.04`
- `Build Quality (arm64)` runs on `macos-15` (Apple Silicon) because current Google Linux `aapt2` artifacts are `x86-64` only, while macOS `aapt2` is universal (`x86_64` + `arm64`)
- `Android Tests` consume APK artifacts from both build jobs and execute a `2 x 2` matrix over `build host x emulator host`
- `Android Tests (x86_64)` run on `ubuntu-24.04` with `x86_64` system images and `testedAbi = "x86_64"`
- `Android Tests (arm64)` run on `ubuntu-24.04-arm` with `arm64-v8a` system images and `testedAbi = "arm64-v8a"`
- No CI stage is reserved only for nightly; the same gate set is executed on pull requests

## Security Rules

- Pull requests do not require repository secrets
- Secret-dependent release signing or publishing must stay outside PR CI
- Dependency updates must pass dependency review before merge
