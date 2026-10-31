#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if ! command -v kubectl >/dev/null 2>&1; then
  echo "Required command not found: kubectl" >&2
  exit 1
fi

kubectl apply -f "$SCRIPT_DIR/event-exporter-service-account.yaml"
kubectl apply -f "$SCRIPT_DIR/event-exporter-rbac.yaml"
kubectl apply -f "$SCRIPT_DIR/event-exporter-configmap.yaml"
kubectl apply -f "$SCRIPT_DIR/event-exporter-deployment.yaml"

kubectl rollout status \
  deployment/kubernetes-event-exporter \
  --namespace chatting-alarm \
  --timeout=3m

kubectl get pods \
  --namespace chatting-alarm \
  --selector app=kubernetes-event-exporter
