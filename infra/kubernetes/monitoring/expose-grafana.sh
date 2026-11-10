#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
for command_name in kubectl terraform aws envsubst python3; do
  command -v "$command_name" >/dev/null 2>&1 || { echo "Required command not found: $command_name" >&2; exit 1; }
done
: "${GRAFANA_ALLOWED_CIDRS:?Set your administrator public IPv4 CIDR, e.g. YOUR_PUBLIC_IP/32}"
export GRAFANA_ALLOWED_CIDRS
# Validate before creating any cluster resources. Reject unrestricted public access.
python3 - <<'PY'
import ipaddress, os
for item in os.environ['GRAFANA_ALLOWED_CIDRS'].split(','):
    network = ipaddress.ip_network(item.strip(), strict=True)
    if network.version != 4 or network.prefixlen == 0:
        raise SystemExit('Use restricted IPv4 CIDRs; 0.0.0.0/0 is not allowed.')
PY
TF_DIR="$SCRIPT_DIR/../../aws"
export AWS_REGION="$(terraform -chdir="$TF_DIR" output -raw aws_region)"
export ACM_CERTIFICATE_ARN="$(terraform -chdir="$TF_DIR" output -raw acm_certificate_arn)"
certificate_status="$(aws acm describe-certificate --certificate-arn "$ACM_CERTIFICATE_ARN" --region "$AWS_REGION" --query Certificate.Status --output text)"
[[ "$certificate_status" == "ISSUED" ]] || { echo "ACM certificate must be ISSUED: $certificate_status" >&2; exit 1; }
kubectl get ingressclass alb >/dev/null
kubectl get service monitoring-grafana -n monitoring >/dev/null
envsubst '${ACM_CERTIFICATE_ARN} ${GRAFANA_ALLOWED_CIDRS}' < "$SCRIPT_DIR/grafana-ingress.yaml" | kubectl apply -f -
echo "Wait for ADDRESS, then add a DNS CNAME: grafana -> ADDRESS (no https:// or port)."
kubectl get ingress monitoring-grafana -n monitoring
