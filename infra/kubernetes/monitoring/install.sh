#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CHART_VERSION="93.1.0"
for command_name in helm kubectl; do
  command -v "$command_name" >/dev/null 2>&1 || { echo "Required command not found: $command_name" >&2; exit 1; }
done
# No AWS credential changes or EBS driver installation are performed here.
kubectl get csidriver ebs.csi.aws.com >/dev/null
kubectl rollout status deployment/ebs-csi-controller -n kube-system --timeout=120s
kubectl get service chatting-alarm-backend -n chatting-alarm >/dev/null
kubectl apply -f "$SCRIPT_DIR/storageclass.yaml"
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts --force-update
helm repo update prometheus-community
helm upgrade --install monitoring prometheus-community/kube-prometheus-stack \
  --version "$CHART_VERSION" \
  --namespace monitoring --create-namespace \
  --values "$SCRIPT_DIR/values.yaml" \
  --wait --timeout 15m
kubectl wait --for=condition=Established crd/servicemonitors.monitoring.coreos.com --timeout=120s
kubectl apply -f "$SCRIPT_DIR/backend-servicemonitor.yaml"
kubectl get pods,pvc -n monitoring
