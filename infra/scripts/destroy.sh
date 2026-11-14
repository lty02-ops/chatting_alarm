#!/usr/bin/env bash
# Explicit confirmation is required; never called by deployment scripts.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
require_commands aws terraform kubectl python3
DESTROY_TMP="$(mktemp -d)"
trap 'rm -rf -- "$DESTROY_TMP"' EXIT
export KUBECONFIG="$DESTROY_TMP/kubeconfig"
REGION="$(tf_output aws_region)"
export AWS_DEFAULT_REGION="$REGION"
aws eks update-kubeconfig --name "$(tf_output eks_cluster_name)" --region "$REGION"
kubectl get namespace chatting-alarm >/dev/null
terraform -chdir="$TF_DIR" plan -destroy -out="$DESTROY_TMP/destroy.tfplan"
printf '\nThis deletes the application, database, uploaded files, images and monitoring EBS data.\nStop Jenkins deployments first. The Terraform state bucket is retained.\n'
read -r -p 'Type DELETE to continue: ' confirmation
[[ "$confirmation" == DELETE ]] || { echo "Cancelled."; exit 1; }
# Capture only the two project Ingresses and their ALBs before deleting them.
kubectl get ingress -A -o json > "$DESTROY_TMP/ingresses.json"
aws elbv2 describe-load-balancers --region "$REGION" --output json > "$DESTROY_TMP/albs.json"
python3 - "$DESTROY_TMP" <<'PY'
import json, sys
from pathlib import Path
p = Path(sys.argv[1])
known = {('chatting-alarm', 'chatting-alarm-ingress'), ('monitoring', 'monitoring-grafana')}
items = [i for i in json.loads((p/'ingresses.json').read_text())['items']
         if (i['metadata']['namespace'], i['metadata']['name']) in known]
hosts = {entry.get('hostname') for i in items
         for entry in i.get('status', {}).get('loadBalancer', {}).get('ingress', [])}
arns = [lb['LoadBalancerArn'] for lb in json.loads((p/'albs.json').read_text())['LoadBalancers']
        if lb['DNSName'] in hosts]
(p/'alb-arns').write_text(''.join(a+'\n' for a in arns))
(p/'ingress-targets').write_text(''.join(i['metadata']['namespace']+' '+i['metadata']['name']+'\n' for i in items))
PY
while read -r namespace name; do
  kubectl delete ingress "$name" -n "$namespace" --wait=true --timeout=5m
done < "$DESTROY_TMP/ingress-targets"
while read -r arn; do
  aws elbv2 wait load-balancers-deleted --load-balancer-arns "$arn" --region "$REGION"
done < "$DESTROY_TMP/alb-arns"
# Retain volumes must be reclaimed while the EBS CSI driver still exists.
kubectl get pv -o json > "$DESTROY_TMP/pvs.json"
python3 - "$DESTROY_TMP" <<'PY'
import json, re, sys
from pathlib import Path
p = Path(sys.argv[1])
rows = []
for pv in json.loads((p/'pvs.json').read_text())['items']:
    spec = pv['spec']
    if spec.get('storageClassName') != 'monitoring-gp3' or spec.get('claimRef', {}).get('namespace') != 'monitoring':
        continue
    csi = spec.get('csi', {})
    volume = csi.get('volumeHandle', '')
    if csi.get('driver') != 'ebs.csi.aws.com' or not re.fullmatch(r'vol-[0-9a-f]+', volume):
        raise SystemExit('Unexpected monitoring volume; inspect PV '+pv['metadata']['name'])
    rows.append(pv['metadata']['name']+' '+volume+'\n')
(p/'volumes').write_text(''.join(rows))
PY
while read -r pv volume; do
  kubectl patch pv "$pv" --type=merge -p '{"spec":{"persistentVolumeReclaimPolicy":"Delete"}}'
done < "$DESTROY_TMP/volumes"
# --ignore-not-found handles an environment where monitoring was never installed.
kubectl delete namespace monitoring --ignore-not-found --wait=true --timeout=5m
while read -r pv volume; do
  kubectl wait --for=delete "pv/$pv" --timeout=5m
  aws ec2 wait volume-deleted --volume-ids "$volume" --region "$REGION"
done < "$DESTROY_TMP/volumes"
terraform -chdir="$TF_DIR" apply "$DESTROY_TMP/destroy.tfplan"
rm -f -- "$ROOT_DIR/deployment-info.json"
echo "Resources deleted. Terraform state bucket retained."
