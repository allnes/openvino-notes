# Local CI Reproduction

```text
local-ci-flow
|-- setup toolchain
|-- run foundation
|-- run debug build and host unit tests
|-- run coverage
|-- optionally run
|   |-- release
|   |-- secrets
|   |-- preflight
|   `-- codeql-build
`-- optionally run
    `-- android-instrumentation
```

```text
toolchain
|-- Java
|   `-- JDK 17
|-- Android SDK
|   |-- command-line tools
|   |-- platform-tools
|   |-- platforms;android-36
|   `-- build-tools;36.0.0
`-- Git
```

```text
verification-note
`-- current host
    `-- macOS arm64
        `-- JDK 17 was configured from Homebrew openjdk@17
        `-- quality, release, CodeQL build, preflight, APK task validation, gitleaks, and emulator instrumentation were reproduced locally
```

This guide is written to be executable on Linux, macOS, and Windows. Where the repository already has OS-specific scripts, those scripts are used directly. Where the repository only has a Linux helper, the equivalent native command is shown for the other operating systems.

```text
macos-validated-path
|-- host
|   `-- macOS arm64
|-- Java
|   `-- Homebrew openjdk@17
|-- Android SDK root
|   `-- ~/Library/Android/sdk
`-- emulator
    `-- android-34 google_apis arm64-v8a AVD
```

## Setup

```text
linux-macos
|-- optional Java
|   |-- export JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home"
|   `-- export PATH="$JAVA_HOME/bin:$PATH"
|-- export ANDROID_API_LEVEL=36
|-- export ANDROID_BUILD_TOOLS=36.0.0
|-- export INSTALL_SYSTEM_IMAGE=false
|-- export ANDROID_SYSTEM_IMAGE=
`-- bash .github/scripts/setup/install_android_sdk_packages.sh
```

```text
windows-powershell
|-- $env:ANDROID_API_LEVEL = "36"
|-- $env:ANDROID_BUILD_TOOLS = "36.0.0"
|-- $env:INSTALL_SYSTEM_IMAGE = "false"
|-- $env:ANDROID_SYSTEM_IMAGE = ""
`-- .\.github\scripts\setup\install_android_sdk_packages_windows.ps1
```

```text
emulator-note
`-- if local instrumentation is needed
    |-- set INSTALL_SYSTEM_IMAGE=true
    `-- provide a system image matching the host architecture
```

Linux or macOS:

```bash
export JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_API_LEVEL=36
export ANDROID_BUILD_TOOLS=36.0.0
export INSTALL_SYSTEM_IMAGE=false
export ANDROID_SYSTEM_IMAGE=

bash .github/scripts/setup/install_android_sdk_packages.sh
```

Windows PowerShell:

```powershell
$env:ANDROID_API_LEVEL = "36"
$env:ANDROID_BUILD_TOOLS = "36.0.0"
$env:INSTALL_SYSTEM_IMAGE = "false"
$env:ANDROID_SYSTEM_IMAGE = ""

.\.github\scripts\setup\install_android_sdk_packages_windows.ps1
```

macOS note:

```bash
export ANDROID_SDK_ROOT="$HOME/Library/Android/sdk"
export ANDROID_HOME="$ANDROID_SDK_ROOT"
```

## Main Developer Gate

```text
main-local-gate
|-- foundation
|   `-- bash .github/scripts/quality/run_foundation.sh
|-- debug-build-and-unit-tests
|   `-- bash .github/scripts/quality/run_debug_build_and_unit_tests.sh
`-- coverage
    `-- bash .github/scripts/quality/run_coverage.sh
```

```text
windows-main-local-gate
|-- debug-build-and-unit-tests
|   `-- .\.github\scripts\quality\run_debug_build_and_unit_tests_windows.ps1
`-- foundation-and-coverage
    `-- .\gradlew.bat ktlintCheck detekt ai:lintDebug app:lintDebug data:lintDebug domain:lintDebug ai:testDebugUnitTest app:testDebugUnitTest data:testDebugUnitTest domain:testDebugUnitTest koverXmlReport koverVerify --stacktrace
```

Linux or macOS:

```bash
bash .github/scripts/quality/run_foundation.sh
bash .github/scripts/quality/run_debug_build_and_unit_tests.sh
bash .github/scripts/quality/run_coverage.sh
```

Windows PowerShell:

```powershell
.\.github\scripts\quality\run_debug_build_and_unit_tests_windows.ps1

.\gradlew.bat ktlintCheck detekt ai:lintDebug app:lintDebug data:lintDebug domain:lintDebug ai:testDebugUnitTest app:testDebugUnitTest data:testDebugUnitTest domain:testDebugUnitTest koverXmlReport koverVerify --stacktrace
```

## Checks

```text
foundation
|-- source-of-truth
|   `-- [run_foundation.sh](/Users/anesterov/repos/openvino-notes/.github/scripts/quality/run_foundation.sh)
|-- tasks
|   |-- ktlintCheck
|   |-- detekt
|   |-- ai:lintDebug
|   |-- app:lintDebug
|   |-- data:lintDebug
|   `-- domain:lintDebug
|-- linux-macos
|   `-- bash .github/scripts/quality/run_foundation.sh
|-- windows
|   `-- .\gradlew.bat ktlintCheck detekt ai:lintDebug app:lintDebug data:lintDebug domain:lintDebug --stacktrace
`-- outputs
    |-- **/build/reports/detekt/
    |-- **/build/reports/ktlint/
    |-- **/build/reports/lint-results-*.html
    `-- **/build/reports/lint-results-*.xml
```

```text
debug-build-and-unit-tests
|-- source-of-truth
|   |-- [run_debug_build_and_unit_tests.sh](/Users/anesterov/repos/openvino-notes/.github/scripts/quality/run_debug_build_and_unit_tests.sh)
|   `-- [run_debug_build_and_unit_tests_windows.ps1](/Users/anesterov/repos/openvino-notes/.github/scripts/quality/run_debug_build_and_unit_tests_windows.ps1)
|-- tasks
|   |-- ai:assembleDebug
|   |-- app:assembleDebug
|   |-- app:assembleDebugAndroidTest
|   |-- data:assembleDebug
|   |-- domain:assembleDebug
|   |-- ai:testDebugUnitTest
|   |-- app:testDebugUnitTest
|   |-- data:testDebugUnitTest
|   `-- domain:testDebugUnitTest
|-- linux-macos
|   `-- bash .github/scripts/quality/run_debug_build_and_unit_tests.sh
|-- windows
|   `-- .\.github\scripts\quality\run_debug_build_and_unit_tests_windows.ps1
`-- outputs
    |-- app/build/outputs/apk/debug/app-debug.apk
    |-- app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    |-- **/build/test-results/
    |-- **/build/reports/tests/
    `-- **/build/outputs/unit_test_code_coverage/
```

```text
coverage
|-- source-of-truth
|   `-- [run_coverage.sh](/Users/anesterov/repos/openvino-notes/.github/scripts/quality/run_coverage.sh)
|-- tasks
|   |-- ai:testDebugUnitTest
|   |-- app:testDebugUnitTest
|   |-- data:testDebugUnitTest
|   |-- domain:testDebugUnitTest
|   |-- koverXmlReport
|   `-- koverVerify
|-- linux-macos
|   `-- bash .github/scripts/quality/run_coverage.sh
|-- windows
|   `-- .\gradlew.bat ai:testDebugUnitTest app:testDebugUnitTest data:testDebugUnitTest domain:testDebugUnitTest koverXmlReport koverVerify --stacktrace
`-- outputs
    `-- **/build/reports/kover/
```

```text
release
|-- source-of-truth
|   |-- [assemble_release.sh](/Users/anesterov/repos/openvino-notes/.github/scripts/release/assemble_release.sh)
|   `-- [lint_release.sh](/Users/anesterov/repos/openvino-notes/.github/scripts/release/lint_release.sh)
|-- linux-macos
|   |-- bash .github/scripts/release/assemble_release.sh
|   `-- bash .github/scripts/release/lint_release.sh
`-- windows
    |-- .\gradlew.bat ai:assembleRelease app:assembleRelease data:assembleRelease domain:assembleRelease --stacktrace
    `-- .\gradlew.bat ai:lintRelease app:lintRelease data:lintRelease domain:lintRelease --stacktrace
```

```text
secrets
|-- source-of-truth
|   `-- [run_gitleaks.sh](/Users/anesterov/repos/openvino-notes/.github/scripts/security/run_gitleaks.sh)
|-- linux
|   `-- bash .github/scripts/security/run_gitleaks.sh
`-- macos-windows
    |-- install native gitleaks
    `-- run
        `-- gitleaks detect --source . --report-format sarif --report-path build/reports/gitleaks/gitleaks.sarif --redact
```

```text
preflight
|-- source-of-truth
|   `-- [classify_changes.sh](/Users/anesterov/repos/openvino-notes/.github/scripts/preflight/classify_changes.sh)
|-- local-bash-example
|   `-- tmpfile=$(mktemp) && \
|       GITHUB_OUTPUT="$tmpfile" \
|       EVENT_NAME=pull_request \
|       BASE_SHA="$(git merge-base upstream/main HEAD)" \
|       HEAD_SHA="$(git rev-parse HEAD)" \
|       BEFORE_SHA="" \
|       CURRENT_SHA="$(git rev-parse HEAD)" \
|       bash .github/scripts/preflight/classify_changes.sh && \
|       cat "$tmpfile" && rm -f "$tmpfile"
`-- powershell
    `-- create a temp output file, set the same environment variables, and invoke bash
```

Example:

```bash
tmpfile="$(mktemp)"
GITHUB_OUTPUT="$tmpfile" \
EVENT_NAME=pull_request \
BASE_SHA="$(git merge-base upstream/main HEAD)" \
HEAD_SHA="$(git rev-parse HEAD)" \
BEFORE_SHA="" \
CURRENT_SHA="$(git rev-parse HEAD)" \
bash .github/scripts/preflight/classify_changes.sh
cat "$tmpfile"
rm -f "$tmpfile"
```

```text
codeql-build
|-- source-of-truth
|   `-- [build_for_codeql.sh](/Users/anesterov/repos/openvino-notes/.github/scripts/codeql/build_for_codeql.sh)
|-- linux-macos
|   `-- bash .github/scripts/codeql/build_for_codeql.sh
|-- windows
|   `-- .\gradlew.bat clean ai:assembleDebug app:assembleDebug data:assembleDebug domain:assembleDebug --no-build-cache --rerun-tasks --stacktrace
`-- limitation
    |-- reproduces build input
    `-- full local parity still needs CodeQL CLI setup
```

```text
android-instrumentation
|-- source-of-truth
|   |-- [validate_debug_apk_tasks.sh](/Users/anesterov/repos/openvino-notes/.github/scripts/android/validate_debug_apk_tasks.sh)
|   |-- [run_emulator_instrumentation.sh](/Users/anesterov/repos/openvino-notes/.github/scripts/android/run_emulator_instrumentation.sh)
|   `-- [run_all_instrumentation_variants.sh](/Users/anesterov/repos/openvino-notes/.github/scripts/android/run_all_instrumentation_variants.sh)
|-- current-ci-target
|   `-- linux
|       `-- api 34
|           `-- x86_64
|               `-- pixel_7
|-- macos-validated-target
|   `-- local
|       `-- api 34
|           `-- arm64-v8a
|               `-- pixel_7
|-- validate-apk-tasks
|   |-- linux-macos
|   |   `-- bash .github/scripts/android/validate_debug_apk_tasks.sh
|   `-- windows
|       `-- .\gradlew.bat app:assembleDebug app:assembleDebugAndroidTest -m --stacktrace
|-- manual-emulator-flow
|   |-- build
|   |   `-- ./gradlew app:assembleDebug app:assembleDebugAndroidTest --stacktrace
|   |-- boot
|   |   `-- start emulator and wait for full boot
|   `-- run-helper
|       `-- APK_DIR=app/build/outputs/apk bash .github/scripts/android/run_emulator_instrumentation.sh
|-- ci-like-artifact-layout
|   |-- mkdir -p build-artifacts/local
|   |-- cp app/build/outputs/apk/debug/app-debug.apk build-artifacts/local/
|   |-- cp app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk build-artifacts/local/
|   `-- APK_ROOT_DIR=build-artifacts RESULTS_ROOT_DIR=android-test-results bash .github/scripts/android/run_all_instrumentation_variants.sh
`-- notes
    |-- CI uses script-driven emulator execution
    |-- app module defines x86_64 and arm64-v8a managed devices
    |-- workflow currently runs only the Linux x86_64 path
    |-- macOS was validated with an arm64 emulator, not the Linux x86_64 CI image
    `-- run_emulator_instrumentation.sh expects a booted device and installs APKs itself
```

Task validation:

```bash
bash .github/scripts/android/validate_debug_apk_tasks.sh
```

```powershell
.\gradlew.bat app:assembleDebug app:assembleDebugAndroidTest -m --stacktrace
```

Manual emulator reproduction on any OS with a booted emulator:

```bash
./gradlew app:assembleDebug app:assembleDebugAndroidTest --stacktrace
APK_DIR=app/build/outputs/apk bash .github/scripts/android/run_emulator_instrumentation.sh
```

Validated macOS arm64 flow:

```bash
export JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH:$HOME/Library/Android/sdk/emulator:$HOME/Library/Android/sdk/platform-tools"
export ANDROID_SDK_ROOT="$HOME/Library/Android/sdk"
export ANDROID_HOME="$ANDROID_SDK_ROOT"

printf 'no\n' | avdmanager create avd -n pixel7api34Arm64Local -k 'system-images;android-34;google_apis;arm64-v8a' -d 'pixel_7'
./gradlew app:assembleDebug app:assembleDebugAndroidTest --stacktrace
emulator -avd pixel7api34Arm64Local -no-snapshot-save -no-window -noaudio -no-boot-anim -gpu swiftshader_indirect &
adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 5; done
ANDROID_SERIAL=emulator-5554 APK_DIR=app/build/outputs/apk bash .github/scripts/android/run_emulator_instrumentation.sh
adb -s emulator-5554 emu kill
```

Validated result on macOS arm64:

```text
com.itlab.notes.ExampleInstrumentedTest:.

Time: 0.006

OK (1 test)
```

CI-like artifact layout:

```bash
mkdir -p build-artifacts/local
cp app/build/outputs/apk/debug/app-debug.apk build-artifacts/local/
cp app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk build-artifacts/local/
APK_ROOT_DIR=build-artifacts RESULTS_ROOT_DIR=android-test-results bash .github/scripts/android/run_all_instrumentation_variants.sh
```

```text
not-fully-reproducible
|-- dependency review
|-- workflow summaries
|-- artifact upload
`-- full CodeQL action lifecycle
```

```text
developer-practical-rule
`-- for pre-push debugging
    `-- reproducing the Gradle tasks and repository scripts above is usually enough
```

Use Git Bash or WSL on Windows for the bash-based Android helper scripts. The Gradle commands themselves are platform-specific above, but the repository's emulator helper scripts are shell scripts, not PowerShell scripts.
