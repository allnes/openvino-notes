#!/usr/bin/env bash
set -euo pipefail

artifact_root="${1:-coverage-artifacts}"

rm -rf "$artifact_root"

mkdir -p \
  "$artifact_root/aggregate" \
  "$artifact_root/modules"

if [[ -f build/reports/kover/report.xml ]]; then
  cp build/reports/kover/report.xml "$artifact_root/aggregate/report.xml"
fi

for module in ai app data domain; do
  report_path="$module/build/reports/kover/report.xml"
  if [[ -f "$report_path" ]]; then
    mkdir -p "$artifact_root/modules/$module"
    cp "$report_path" "$artifact_root/modules/$module/report.xml"
  fi
done
