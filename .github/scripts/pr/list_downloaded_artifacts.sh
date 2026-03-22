#!/usr/bin/env bash
set -euo pipefail

find "${1:-pr-artifacts}" -maxdepth 8 -type f | sort
