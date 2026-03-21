#!/usr/bin/env bash
set -euo pipefail

summary_file="$(mktemp)"
trap 'rm -f "$summary_file"' EXIT

python3 .github/scripts/quality/build_coverage_summary.py . "$summary_file"

{
  cat "$summary_file"
  echo
  echo "Coverage artifacts are attached to this workflow run."
} >> "${GITHUB_STEP_SUMMARY:?}"
