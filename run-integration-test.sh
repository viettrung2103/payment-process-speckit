#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [[ -x "${ROOT_DIR}/run-mvn-test.sh" ]]; then
  exec "${ROOT_DIR}/run-mvn-test.sh" verify -Dspring.profiles.active=test "$@"
fi

echo "[ERROR] run-mvn-test.sh is missing or not executable."
exit 1