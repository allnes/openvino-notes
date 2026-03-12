#!/usr/bin/env bash
set -euo pipefail

event_name="${EVENT_NAME:?}"
current_sha="${CURRENT_SHA:?}"
github_output="${GITHUB_OUTPUT:?}"

if [[ "$event_name" == "pull_request" ]]; then
  base_sha="${BASE_SHA:?}"
  head_sha="${HEAD_SHA:?}"
  changed="$(git diff --name-only "$base_sha" "$head_sha")"
elif [[ -n "${BEFORE_SHA:-}" && "${BEFORE_SHA}" != "0000000000000000000000000000000000000000" ]]; then
  changed="$(git diff --name-only "$BEFORE_SHA" "$current_sha")"
else
  changed="$(git show --name-only --pretty='' "$current_sha")"
fi

clean_changed="$(printf '%s\n' "$changed" | sed '/^$/d')"

if [[ "$event_name" == "pull_request" ]]; then
  run_android_tests="true"
  run_release="true"
  run_codeql="true"
else
  if printf '%s\n' "$clean_changed" | grep -E '^(app/|ai/|.*/src/androidTest/|.*/src/main/|build.gradle.kts|settings.gradle.kts|gradle/|.github/workflows/)' >/dev/null; then
    run_android_tests="true"
    run_release="true"
  else
    run_android_tests="false"
    run_release="false"
  fi

  if printf '%s\n' "$clean_changed" | grep -E '^(app/|ai/|data/|domain/|build.gradle.kts|settings.gradle.kts|gradle/|.github/workflows/)' >/dev/null; then
    run_codeql="true"
  else
    run_codeql="false"
  fi
fi

{
  echo "run_android_tests=$run_android_tests"
  echo "run_release=$run_release"
  echo "run_codeql=$run_codeql"
  echo "changed_paths<<__EOF__"
  if [[ -n "$clean_changed" ]]; then
    printf '%s\n' "$clean_changed"
  fi
  echo "__EOF__"
} >> "$github_output"
