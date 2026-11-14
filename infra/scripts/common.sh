#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"
TF_DIR="$ROOT_DIR/infra/aws"
export AWS_PAGER=""
require_commands() {
  local name
  for name in "$@"; do
    command -v "$name" >/dev/null 2>&1 || { echo "Required command not found: $name" >&2; exit 1; }
  done
}
tf_output() { terraform -chdir="$TF_DIR" output -raw "$1"; }
