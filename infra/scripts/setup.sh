#!/usr/bin/env bash
# Prepare the existing dev infrastructure; Terraform still asks before applying.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
require_commands aws terraform python3
if [[ ! -f .env ]]; then
  cp .env.example .env
  echo "Created .env. Fill in all six OAuth values, then rerun this script." >&2
  exit 1
fi
python3 infra/aws/upload-backend-env.py --validate-only
python3 infra/scripts/jenkins-deploy.py --configure
if [[ ! -f "$TF_DIR/terraform.tfvars" ]]; then
  cp "$TF_DIR/terraform.tfvars.example" "$TF_DIR/terraform.tfvars"
  echo "Created terraform.tfvars. Set a unique bucket_suffix, then rerun." >&2
  exit 1
fi
if [[ "$(cat "$TF_DIR/terraform.tfvars")" == *replace-with-a-unique-suffix* ]]; then
  echo "Set bucket_suffix in infra/aws/terraform.tfvars before continuing." >&2
  exit 1
fi
aws sts get-caller-identity --query '{Account:Account,Arn:Arn}'
# Read the literal S3 backend settings rather than maintaining a second copy.
backend_setting() {
  python3 - "$TF_DIR/backend.tf" "$1" <<'PY'
import re, sys
from pathlib import Path
text = Path(sys.argv[1]).read_text()
match = re.search(r'\b' + re.escape(sys.argv[2]) + r'\s*=\s*"([^"]+)"', text)
if not match:
    raise SystemExit('Missing literal S3 backend setting: ' + sys.argv[2])
print(match.group(1))
PY
}
STATE_BUCKET="$(backend_setting bucket)"
STATE_REGION="$(backend_setting region)"
if bucket_error="$(aws s3api head-bucket --bucket "$STATE_BUCKET" 2>&1)"; then
  echo "Reusing state bucket: $STATE_BUCKET"
else
  # Access denied and network errors must never be treated as an absent bucket.
  if [[ "$bucket_error" != *'(404)'* ]]; then
    printf '%s\n' "$bucket_error" >&2
    exit 1
  fi
  if [[ "$STATE_REGION" == us-east-1 ]]; then
    aws s3api create-bucket --bucket "$STATE_BUCKET" --region "$STATE_REGION"
  else
    aws s3api create-bucket --bucket "$STATE_BUCKET" --region "$STATE_REGION" \
      --create-bucket-configuration "LocationConstraint=$STATE_REGION"
  fi
  aws s3api put-bucket-versioning --bucket "$STATE_BUCKET" \
    --versioning-configuration Status=Enabled --region "$STATE_REGION"
fi
terraform -chdir="$TF_DIR" init
terraform -chdir="$TF_DIR" apply
if [[ "$(tf_output aws_region)" != ap-northeast-2 || "$(tf_output eks_cluster_name)" != chatting_alarm-eks ]]; then
  echo "These scripts and the secret uploader support the default Seoul/dev environment only." >&2
  exit 1
fi
python3 infra/aws/upload-backend-env.py
echo "Register these ACM validation CNAMEs in your DNS console:"
terraform -chdir="$TF_DIR" output acm_dns_validation_records
echo "Once the certificate is ISSUED, run: bash infra/scripts/deploy-dev.sh"
