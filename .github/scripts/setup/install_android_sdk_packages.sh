#!/usr/bin/env bash
set -euo pipefail

set +o pipefail
yes | sdkmanager --licenses >/dev/null
set -o pipefail

packages=(
  "platforms;android-${ANDROID_API_LEVEL:?}"
  "build-tools;${ANDROID_BUILD_TOOLS:?}"
)

if [[ "${INSTALL_SYSTEM_IMAGE:-false}" == "true" && -n "${ANDROID_SYSTEM_IMAGE:-}" ]]; then
  packages+=("emulator")
  packages+=("${ANDROID_SYSTEM_IMAGE}")
fi

sdkmanager "${packages[@]}"
