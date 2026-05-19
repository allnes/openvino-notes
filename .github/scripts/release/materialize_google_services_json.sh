#!/usr/bin/env bash
# Writes the real Firebase Google services config for trusted CI release builds.
#
# Configure this repository secret (Settings -> Secrets and variables -> Actions):
#   GOOGLE_SERVICES_JSON_BASE64 - base64 of the real app/google-services.json
#
# Pull request builds intentionally keep the checked-in mock google-services.json.

set -euo pipefail

ROOT="${GITHUB_WORKSPACE:-.}"
OUTPUT_PATH="${ROOT}/app/google-services.json"
REQUIRED="${GOOGLE_SERVICES_JSON_REQUIRED:-false}"

if [[ -z "${GOOGLE_SERVICES_JSON_BASE64:-}" ]]; then
  if [[ "${REQUIRED}" == "true" ]]; then
    echo "Google services config is not configured. Missing repository secret:"
    echo "  - GOOGLE_SERVICES_JSON_BASE64"
    echo "Add it under Settings -> Secrets and variables -> Actions, then re-run the workflow."
    exit 1
  fi

  echo "Google services config secret is not configured; keeping checked-in template."
  exit 0
fi

mkdir -p "$(dirname "${OUTPUT_PATH}")"

python3 - "${OUTPUT_PATH}" <<'PY'
import base64
import json
import os
import sys
from pathlib import Path

output_path = Path(sys.argv[1])
payload = os.environ["GOOGLE_SERVICES_JSON_BASE64"]

try:
    decoded = base64.b64decode(payload, validate=True).decode("utf-8")
except Exception as error:
    raise SystemExit(f"GOOGLE_SERVICES_JSON_BASE64 is not valid base64 UTF-8: {error}") from error

try:
    document = json.loads(decoded)
except json.JSONDecodeError as error:
    raise SystemExit(f"GOOGLE_SERVICES_JSON_BASE64 does not decode to valid JSON: {error}") from error

project_info = document.get("project_info", {})
clients = document.get("client", [])
if not isinstance(project_info, dict) or not project_info.get("project_id"):
    raise SystemExit("Decoded google-services.json is missing project_info.project_id.")
if not isinstance(clients, list) or not clients:
    raise SystemExit("Decoded google-services.json is missing at least one client entry.")

output_path.write_text(json.dumps(document, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
PY

echo "Google services config: wrote ${OUTPUT_PATH} from GOOGLE_SERVICES_JSON_BASE64."
