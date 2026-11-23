#!/usr/bin/env bash
# Deletes the local kind cluster.
set -euo pipefail
cd "$(dirname "$0")/.."
KIND="${KIND:-.tools/kind.exe}"
[ -x "$KIND" ] || KIND="$(command -v kind)"
"$KIND" delete cluster --name journal
