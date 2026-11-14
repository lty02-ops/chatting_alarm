#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
require_commands aws terraform kubectl helm python3 envsubst
if [[ -z "${GRAFANA_ALLOWED_CIDRS:-}" ]]; then
  read -r -p "Administrator public IPv4 CIDR (e.g. 203.0.113.10/32): " GRAFANA_ALLOWED_CIDRS
fi
export GRAFANA_ALLOWED_CIDRS
python3 - <<'PY'
import ipaddress, os
for value in os.environ['GRAFANA_ALLOWED_CIDRS'].split(','):
    network = ipaddress.ip_network(value.strip(), strict=True)
    if network.version != 4 or network.prefixlen == 0:
        raise SystemExit('Enter restricted IPv4 CIDRs; 0.0.0.0/0 is not allowed.')
PY
MONITOR_TMP="$(mktemp -d)"
trap 'rm -rf -- "$MONITOR_TMP"' EXIT
export KUBECONFIG="$MONITOR_TMP/kubeconfig"
aws eks update-kubeconfig --name "$(tf_output eks_cluster_name)" --region "$(tf_output aws_region)"
bash infra/kubernetes/monitoring/install.sh
bash infra/kubernetes/monitoring/expose-grafana.sh
ADDRESS=""
for ((attempt=0; attempt<60; attempt++)); do
  ADDRESS="$(kubectl get ingress monitoring-grafana -n monitoring -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')"
  [[ -z "$ADDRESS" ]] || break
  sleep 5
done
[[ -n "$ADDRESS" ]] || { echo "Grafana ALB address is not available yet." >&2; exit 1; }
ADDRESS="$ADDRESS" python3 - <<'PY'
import json, os
from pathlib import Path
path = Path('deployment-info.json')
data = json.loads(path.read_text()) if path.exists() else {}
data['grafanaUrl'] = 'https://grafana.chatting-alarm.p-e.kr'
data['grafanaAlbAddress'] = os.environ['ADDRESS']
path.write_text(json.dumps(data, indent=2) + '\n')
print('Register grafana CNAME -> ' + os.environ['ADDRESS'])
print('Grafana: ' + data['grafanaUrl'] + ' (user: admin)')
PY
printf 'Initial Grafana password (may differ if previously changed): '
kubectl get secret monitoring-grafana -n monitoring -o jsonpath='{.data.admin-password}' | python3 -c 'import base64,sys; print(base64.b64decode(sys.stdin.read()).decode())'
