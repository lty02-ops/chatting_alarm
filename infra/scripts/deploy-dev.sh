#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
require_commands python3
python3 infra/scripts/jenkins-deploy.py
