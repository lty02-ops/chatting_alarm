#!/usr/bin/env bash
# Jenkins-only: build the checked-out main commit and deploy it to EKS.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
require_commands aws terraform python3 git docker kubectl envsubst
docker info >/dev/null
REGION="$(tf_output aws_region)"
CLUSTER="$(tf_output eks_cluster_name)"
CERTIFICATE="$(tf_output acm_certificate_arn)"
STATUS="$(aws acm describe-certificate --certificate-arn "$CERTIFICATE" --region "$REGION" --query Certificate.Status --output text)"
if [[ "$STATUS" != ISSUED ]]; then
  terraform -chdir="$TF_DIR" output acm_dns_validation_records
  echo "Register the DNS records and rerun after ACM reports ISSUED (current: $STATUS)." >&2
  exit 1
fi
if [[ -n "$(git status --porcelain --untracked-files=normal)" ]]; then
  echo "Commit or remove local changes before deploying so the image tag identifies its source." >&2
  exit 1
fi
export IMAGE_TAG="$(git rev-parse HEAD)"
BACKEND_REPO="$(tf_output backend_ecr_repository_url)"
FRONTEND_REPO="$(tf_output frontend_ecr_repository_url)"
REGISTRY="${BACKEND_REPO%%/*}"
# Do not replace the user's Docker credentials or kubeconfig.
DEPLOY_TMP="$(mktemp -d)"
trap 'rm -rf -- "$DEPLOY_TMP"' EXIT
export DOCKER_CONFIG="$DEPLOY_TMP/docker"
export KUBECONFIG="$DEPLOY_TMP/kubeconfig"
mkdir -p "$DOCKER_CONFIG"
aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "$REGISTRY"
docker build --platform linux/amd64 -t "$BACKEND_REPO:$IMAGE_TAG" backend
docker build --platform linux/amd64 --build-arg VITE_API_BASE_URL=https://api.chatting-alarm.p-e.kr \
  -t "$FRONTEND_REPO:$IMAGE_TAG" frontend
docker push "$BACKEND_REPO:$IMAGE_TAG"
docker push "$FRONTEND_REPO:$IMAGE_TAG"
aws eks update-kubeconfig --name "$CLUSTER" --region "$REGION"
bash infra/kubernetes/apply.sh
# ALB creation may finish after application rollouts.
ALB_ADDRESS=""
for ((attempt=0; attempt<60; attempt++)); do
  ALB_ADDRESS="$(kubectl get ingress chatting-alarm-ingress -n chatting-alarm -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')"
  [[ -z "$ALB_ADDRESS" ]] || break
  sleep 5
done
if [[ -z "$ALB_ADDRESS" ]]; then
  echo "Ingress has no ALB address yet. Check its events and rerun after resolving the error." >&2
  exit 1
fi
ALB_ADDRESS="$ALB_ADDRESS" python3 - <<'PY'
import json, os
from pathlib import Path
api = 'https://api.chatting-alarm.p-e.kr'
data = {
    'imageTag': os.environ['IMAGE_TAG'],
    'appUrl': 'https://app.chatting-alarm.p-e.kr',
    'apiUrl': api,
    'albAddress': os.environ['ALB_ADDRESS'],
    'socialProviderCallbackUrls': {
        provider: f'{api}/login/oauth2/code/{provider}'
        for provider in ('google', 'kakao', 'naver')
    },
}
Path('deployment-info.json').write_text(json.dumps(data, indent=2) + '\n')
print(json.dumps(data, indent=2))
PY
echo "Deployment finished. Set app/api DNS CNAMEs to albAddress and register the callback URLs in deployment-info.json."
