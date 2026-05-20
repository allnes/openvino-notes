[![CI](https://github.com/embedded-dev-research/openvino-notes/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/embedded-dev-research/openvino-notes/actions/workflows/ci.yml?query=branch%3Amain)
[![Nightly OpenVINO Android Prebuilds](https://github.com/embedded-dev-research/openvino-notes/actions/workflows/nightly-openvino-android-prebuilds.yml/badge.svg?branch=main)](https://github.com/embedded-dev-research/openvino-notes/actions/workflows/nightly-openvino-android-prebuilds.yml?query=branch%3Amain)
[![CodeQL](https://github.com/embedded-dev-research/openvino-notes/actions/workflows/codeql.yml/badge.svg?branch=main)](https://github.com/embedded-dev-research/openvino-notes/actions/workflows/codeql.yml?query=branch%3Amain)

# OpenVINO Notes

OpenVINO Notes is a Kotlin Android notes application with local storage, Firebase-based synchronization, and on-device AI powered by OpenVINO.

The application keeps normal note-taking flows separate from AI inference. Notes, folders, media, synchronization, and editor state live in the application layers. OpenVINO Runtime, OpenVINO GenAI, the GenAI Java API bridge, and model bundles are consumed as external build-time assets instead of being stored in git.

## Features

- Notes and folders with a Compose-based Android UI.
- Local persistence and synchronization support through Firebase services.
- On-device text assistance for summary suggestions, tag suggestions, and note rewrites.
- Separate image-tagging pipeline backed by an OpenVINO vision model bundle.
- Android release builds with optional local signing and CI signing through repository secrets.
- CI coverage for formatting, static analysis, lint, unit tests, instrumentation tests, release assembly, security checks, and nightly OpenVINO Android prebuild publication.

## Repository Layout

| Path | Purpose |
| --- | --- |
| `app/` | Android application, Compose UI, dependency injection, editor and sync flows |
| `domain/` | Domain models, repository contracts, and use cases |
| `data/` | Repository implementations, persistence, and mapping |
| `ai/` | OpenVINO integration, model packaging, prompts, output processing, and AI tests |
| `.github/` | CI, release, security, prebuild, and local reproduction scripts |
| `docs/` | Developer and architecture documentation |

## Requirements

- JDK 21 for repository scripts and CI parity.
- Android SDK platform `android-37` and Build Tools `37.0.0` for app builds.
- Android platform-tools for install and connected-test workflows.
- Python 3 for OpenVINO prebuild and model bundle download helpers.
- Network access to GitHub release assets during `:ai:preBuild`.
- A real Android device or emulator matching the selected native ABI when running the app or instrumentation tests.

The default OpenVINO Android ABI is `arm64-v8a`. Use `-PopenvinoAndroidAbi=x86_64` for x86_64 emulator builds.

## Local Configuration

The app uses Firebase and the Google Services Gradle plugin. Local app builds require a real Firebase config at:

```text
app/google-services.json
```

This file is intentionally ignored by git. CI materializes it from the `GOOGLE_SERVICES_JSON_BASE64` repository secret.

Release signing is also local or secret-backed. For local signed release builds, provide ignored files:

```text
app/keystore.properties
app/ci-release-signing.jks
```

Never commit Firebase configs, signing keys, downloaded model bundles, extracted OpenVINO runtime packages, APKs, or device logs.

## Quick Start

Clone the repository:

```bash
git clone https://github.com/embedded-dev-research/openvino-notes.git
cd openvino-notes
```

Configure the Android SDK path if Gradle cannot discover it automatically:

```bash
printf 'sdk.dir=%s\n' "$ANDROID_SDK_ROOT" > local.properties
```

Build a debug APK for the default `arm64-v8a` runtime:

```bash
./gradlew :app:assembleDebug --stacktrace
```

Build for an x86_64 Android emulator:

```bash
./gradlew :app:assembleDebug -PopenvinoAndroidAbi=x86_64 --stacktrace
```

Install on a connected device:

```bash
./gradlew :app:installDebug
```

If GitHub API rate limits affect public release-asset downloads, export a token with read access:

```bash
export GITHUB_TOKEN=<token>
```

## OpenVINO Assets

Normal application builds do not compile OpenVINO locally. Gradle downloads the required rolling release assets during `:ai:preBuild`:

- `openvino-android-common-nightly.zip`
- `openvino-android-runtime-<abi>-nightly.zip`
- `on-device-llm-openvino-int4.zip`
- `on-device-vision-openvino.zip`

Useful Gradle properties:

```bash
./gradlew :app:assembleRelease \
  -PopenvinoAndroidAbi=x86_64 \
  -PopenvinoAndroidPrebuildRepo=embedded-dev-research/openvino-notes \
  -PopenvinoAndroidPrebuildReleaseTag=openvino-android-prebuilds-nightly \
  -PonDeviceLlmBundleReleaseTag=openvino-llm-models-nightly \
  -PonDeviceVisionBundleReleaseTag=openvino-vision-models-nightly
```

To use an already extracted local LLM bundle:

```bash
./gradlew :app:assembleDebug \
  -PonDeviceLlmPreparedDir=/absolute/path/to/on-device-llm-openvino
```

## Validation

Use the same scripts that back the CI jobs:

```bash
bash .github/scripts/quality/run_foundation.sh
bash .github/scripts/quality/run_debug_build_and_unit_tests.sh
bash .github/scripts/quality/run_coverage.sh
```

Run release assembly and release lint:

```bash
bash .github/scripts/release/assemble_release.sh
bash .github/scripts/release/lint_release.sh
```

Run on-device LLM validation:

```bash
./gradlew :ai:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.itlab.ai.OnDeviceLlmMultilingualInstrumentedTest \
  --stacktrace
```

Run on-device image-tagging validation:

```bash
./gradlew :ai:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.itlab.ai.OpenVinoYoloImageTaggerInstrumentedTest \
  --stacktrace
```

## Documentation

- [Documentation index](docs/README.md)
- [Developer guide](docs/developer/README.md)
- [Project overview](docs/developer/project.md)
- [Local CI reproduction](docs/developer/ci-local.md)
- [On-device AI](docs/developer/on-device-ai.md)
- [Architecture overview](docs/architecture/overview.md)

## License

This project is licensed under the Apache License 2.0. See [LICENSE](LICENSE).
