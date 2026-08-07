#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TF_DIR="$SCRIPT_DIR/../aws"

for command_name in terraform kubectl envsubst aws; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "Required command not found: $command_name" >&2
    exit 1
  fi
done

terraform_output() {
  terraform -chdir="$TF_DIR" output -raw "$1"
}

export AWS_REGION="$(terraform_output aws_region)"
export EKS_CLUSTER_NAME="$(terraform_output eks_cluster_name)"
export VPC_ID="$(terraform_output vpc_id)"
export ALB_CONTROLLER_ROLE_ARN="$(terraform_output alb_controller_role_arn)"
export BACKEND_IRSA_ROLE_ARN="$(terraform_output backend_irsa_role_arn)"
export ALB_SECURITY_GROUP_ID="$(terraform_output alb_security_group_id)"
export ACM_CERTIFICATE_ARN="$(terraform_output acm_certificate_arn)"
export BACKEND_ECR_REPOSITORY_URL="$(terraform_output backend_ecr_repository_url)"
export FRONTEND_ECR_REPOSITORY_URL="$(terraform_output frontend_ecr_repository_url)"
export RDS_ENDPOINT="$(terraform_output rds_endpoint)"
export RDS_MASTER_SECRET_ARN="$(terraform_output rds_master_secret_arn)"
export REDIS_PRIMARY_ENDPOINT="$(terraform_output redis_primary_endpoint)"
export S3_BUCKET_NAME="$(terraform_output s3_bucket_name)"
export IMAGE_TAG="${IMAGE_TAG:-latest}"

certificate_status="$(
  aws acm describe-certificate \
    --certificate-arn "$ACM_CERTIFICATE_ARN" \
    --region "$AWS_REGION" \
    --query 'Certificate.Status' \
    --output text
)"

if [[ "$certificate_status" != "ISSUED" ]]; then
  echo "ACM certificate is not issued yet (status: $certificate_status)." >&2
  echo "Add the CNAME values from: terraform -chdir=$TF_DIR output acm_dns_validation_records" >&2
  exit 1
fi

apply_template() {
  local manifest="$1"
  echo "Applying $manifest"
  envsubst < "$SCRIPT_DIR/$manifest" | kubectl apply -f -
}

echo "Applying static prerequisites"
kubectl apply -f "$SCRIPT_DIR/namespace.yaml"
kubectl apply -f "https://github.com/cert-manager/cert-manager/releases/download/v1.20.0/cert-manager.yaml"
kubectl wait \
  --namespace cert-manager \
  --for=condition=Available \
  deployment/cert-manager-webhook \
  --timeout=180s

apply_template "alb-controller/service-account.yaml"
apply_template "alb-controller/controller.yaml"
kubectl apply -f "$SCRIPT_DIR/alb-controller/ingress-class.yaml"

apply_template "backend-service-account.yaml"
kubectl apply -f "$SCRIPT_DIR/frontend-service-account.yaml"
apply_template "secret-provider-class.yaml"
apply_template "application/configmap.yaml"

apply_template "backend-deployment.yaml"
kubectl apply -f "$SCRIPT_DIR/backend-service.yaml"
kubectl apply -f "$SCRIPT_DIR/backend-pdb.yaml"

apply_template "frontend-deployment.yaml"
kubectl apply -f "$SCRIPT_DIR/frontend-service.yaml"
kubectl apply -f "$SCRIPT_DIR/frontend-pdb.yaml"

apply_template "ingress.yaml"

echo "Waiting for application rollouts"
kubectl rollout status deployment/chatting-alarm-backend -n chatting-alarm --timeout=5m
kubectl rollout status deployment/chatting-alarm-frontend -n chatting-alarm --timeout=5m

echo "Deployment completed"
kubectl get ingress chatting-alarm-ingress -n chatting-alarm
