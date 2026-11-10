# 모니터링 가이드

## 사전 준비

[AWS 배포 가이드](deployment.md)에 따라 앱과 DNS를 준비한 뒤 Bash/WSL에서 진행합니다. Helm 3, kubectl, AWS CLI, Terraform, Python 3, `envsubst`가 필요합니다. 현재 kubeconfig가 의도한 EKS 클러스터를 가리키는지 `kubectl config current-context`로 확인합니다. Helm 차트는 CRD, ClusterRole 등을 생성하므로 앱 네임스페이스 Edit 권한만으로는 설치할 수 없습니다.

`infra/aws/ebs-csi.tf`에서 EBS CSI 드라이버, 전용 IRSA Role, `AmazonEBSCSIDriverPolicyV2` 권한을 관리합니다. 드라이버가 없다면 기존 Terraform state와 설정을 사용해 적용합니다. 기존 추가 기능을 수동 설치했다면 중복 생성하지 말고 Terraform import부터 진행합니다.

```bash
terraform -chdir=infra/aws plan
terraform -chdir=infra/aws apply
```

계획에서 의도한 EBS CSI 관련 추가 사항을 확인합니다. 다른 리소스의 삭제·교체가 나타나면 원인을 확인한 후 적용합니다. 드라이버 설치 후 다음을 확인합니다.

```bash
kubectl get csidriver ebs.csi.aws.com
kubectl rollout status deployment/ebs-csi-controller -n kube-system --timeout=120s
kubectl get nodes
kubectl top nodes
```

`kubectl top`은 Metrics Server가 있어야 동작합니다. 앱과 CloudWatch 에이전트 외에 Prometheus·Grafana·Operator·노드 exporter 등이 실행될 여유가 필요합니다. Pending이나 OOM이 발생하면 노드 용량과 리소스 제한을 확인합니다. EBS와 추가 컴퓨팅 비용이 발생합니다.

## 설치

```bash
bash infra/kubernetes/monitoring/install.sh
```

차트 버전은 `93.1.0`으로 고정합니다. 설치 스크립트는 EBS 드라이버와 백엔드 Service를 확인한 뒤 StorageClass, Helm 릴리스, ServiceMonitor를 순서대로 적용합니다. 앱의 `apply.sh` 및 Jenkins 이미지 갱신 작업과 별개입니다. 차트 버전 변경 시 [업그레이드 안내](https://github.com/prometheus-community/helm-charts/tree/main/charts/kube-prometheus-stack#upgrading-chart)를 먼저 확인합니다. 기존 CRD는 Helm 업그레이드만으로 갱신되지 않을 수 있습니다.

Prometheus는 `release: monitoring` 라벨을 가진 ServiceMonitor만 선택하며 `monitoring`, `chatting-alarm` 네임스페이스를 탐색합니다. 백엔드 ServiceMonitor는 `app: chatting-alarm-backend` Service의 `http` 포트를 통해 각 Pod를 30초마다 수집합니다. 관리형 EKS에서 직접 수집하기 어려운 etcd·controller-manager·scheduler·kube-proxy 수집은 비활성화합니다. Alertmanager도 비활성화하며 외부 알림은 기존 CloudWatch 구성을 사용합니다.

## 외부 메트릭 차단 적용

`infra/kubernetes/ingress.yaml`은 외부 메트릭 요청에 403을 반환합니다. 최신 `apply.sh`로 처음 배포했다면 이미 적용됩니다. 이전 환경에 차단 규칙만 반영하려면 다음 명령으로 Ingress를 다시 적용합니다. 로컬 템플릿을 그대로 `kubectl apply`하면 `${...}` 값이 치환되지 않으므로 주의합니다.

```bash
export ACM_CERTIFICATE_ARN="$(terraform -chdir=infra/aws output -raw acm_certificate_arn)"
export ALB_SECURITY_GROUP_ID="$(terraform -chdir=infra/aws output -raw alb_security_group_id)"
envsubst '${ACM_CERTIFICATE_ARN} ${ALB_SECURITY_GROUP_ID}' < infra/kubernetes/ingress.yaml | kubectl apply -f -
curl -i https://api.chatting-alarm.p-e.kr/actuator/prometheus
```

ALB 반영 후 외부 요청은 403이어야 합니다. 내부 ServiceMonitor 수집은 계속 허용됩니다. 이 규칙은 해당 ALB 경로만 보호하므로 백엔드 Service를 NodePort/LoadBalancer로 별도 공개하지 않습니다.

## Grafana 도메인 접속 설정

기존 Terraform 인증서는 `*.chatting-alarm.p-e.kr`를 포함하므로 새 인증서를 요청할 필요는 없습니다. 기존 인증서 상태가 ISSUED여야 하며 AWS Load Balancer Controller가 설치되어 있어야 합니다.

설치 스크립트 실행 후 다음 명령을 실행합니다. IP에는 브라우저가 사용하는 네트워크의 실제 공인 IPv4 주소를 넣습니다. 여러 관리자 네트워크는 쉼표로 구분합니다. `0.0.0.0/0`은 허용하지 않습니다.

브라우저를 사용하는 컴퓨터에서 공인 IP를 확인합니다. 출력된 주소 뒤에 `/32`를 붙입니다. 아래 자리표시자는 실제 값으로 바꿉니다.

```bash
curl -4 https://checkip.amazonaws.com

export GRAFANA_ALLOWED_CIDRS="실제_관리자_공인_IP/32"
bash infra/kubernetes/monitoring/expose-grafana.sh
kubectl get ingress monitoring-grafana -n monitoring -w
```

이 스크립트는 Terraform state에서 기존 인증서 ARN과 리전을 읽으며 Terraform apply는 실행하지 않습니다. 모니터링 네임스페이스에 전용 ALB를 생성합니다. 앱 ALB와 보안 그룹을 공유하면 관리자 IP 제한을 분리하기 어려우므로 별도로 생성하며 ALB 비용이 추가됩니다. Prometheus와 Grafana의 Service는 ClusterIP로 유지됩니다. Helm의 Grafana Ingress는 끄고 별도 매니페스트로 관리합니다.

### DNS 등록

ADDRESS가 생성되면 `chatting-alarm.p-e.kr`의 DNS에 다음 CNAME을 추가합니다.

| 항목 | 값 |
|---|---|
| 타입 | CNAME |
| 호스트 | grafana (전체 주소를 요구하면 grafana.chatting-alarm.p-e.kr) |
| 대상 | monitoring-grafana Ingress의 ADDRESS |

대상에는 `https://`, 경로, 포트를 넣지 않습니다. 기존 동일 호스트의 A/CNAME과 충돌하지 않도록 확인합니다. DNS 반영 후 허용한 관리자 네트워크에서 **https://grafana.chatting-alarm.p-e.kr**에 접속합니다. 네임서버가 다른 사업자로 위임되어 있다면 실제 권한 DNS에서 등록해야 합니다.

```bash
nslookup grafana.chatting-alarm.p-e.kr
curl -I https://grafana.chatting-alarm.p-e.kr/login
```

HTTP 요청은 HTTPS로 이동하고, HTTPS에서는 로그인 화면이 표시되어야 합니다. 연결 시간 초과 시 공인 IP 변경·CIDR 설정·보안 그룹을, 503이면 Grafana Pod와 Target Group 상태를 확인합니다. IP가 바뀌면 `GRAFANA_ALLOWED_CIDRS`를 갱신하고 expose 스크립트를 다시 실행합니다.

### 로그인

사용자명은 `admin`이며 초기 비밀번호는 다음 명령으로 조회합니다. 조회한 비밀번호는 공유하거나 저장소에 기록하지 않습니다. 최초 로그인 후 강력한 비밀번호로 변경합니다. 변경 후에는 Secret의 초기 비밀번호와 실제 로그인 비밀번호가 다를 수 있습니다.

```bash
kubectl get secret monitoring-grafana -n monitoring -o jsonpath='{.data.admin-password}' | base64 --decode; echo
```

익명 접속과 사용자 직접 가입은 비활성화되어 있습니다. HTTPS는 ALB에서 종료하고 Grafana는 외부 HTTPS 주소를 기준으로 링크와 Secure 쿠키를 생성합니다. DNS 등록과 AWS 적용은 저장소 파일 작성만으로 실행되지 않습니다.

차트가 Prometheus 데이터 소스와 Kubernetes 대시보드를 자동 등록합니다. 백엔드 전용 대시보드는 별도로 추가할 수 있습니다. Explore에서 `jvm_memory_used_bytes`와 `http_server_requests_seconds_count`를 조회하고 애플리케이션 요청 후 데이터가 나타나는지 확인합니다. 메트릭 수집 요청 자체도 HTTP 카운터에 포함될 수 있습니다.

## Prometheus 및 저장 상태 확인

```bash
kubectl get pods,pvc -n monitoring
kubectl get servicemonitor chatting-alarm-backend -n monitoring
kubectl port-forward -n monitoring svc/monitoring-kube-prometheus-prometheus 9090:9090
```

`http://localhost:9090/targets`에서 백엔드 Pod들이 UP인지 확인합니다. 다음 쿼리는 각 백엔드 수집 대상이 정상일 때 1을 반환합니다.

```promql
up{namespace="chatting-alarm",service="chatting-alarm-backend"}
```

PVC는 Bound, Pod는 Running이어야 합니다. PVC Pending이면 `kubectl describe pvc -n monitoring`과 EBS CSI 컨트롤러 로그에서 IAM·용량·가용 영역을 확인합니다. WaitForFirstConsumer는 Pod 스케줄링 전 PVC가 Pending일 수 있습니다. EBS는 가용 영역에 종속됩니다.

Prometheus는 20Gi PVC에서 최대 7일 또는 15GB를 보존합니다. 보존 크기는 디스크 사용 전체의 상한이 아니므로 여유 공간을 관찰합니다. Grafana는 5Gi PVC를 사용합니다. StorageClass는 Retain 정책이므로 PVC 삭제 후 EBS 볼륨은 수동 회수 전까지 남고 비용이 발생합니다. 단일 인스턴스 구성이므로 장애·재스케줄링 중 중단될 수 있으며 별도의 백업 정책이 필요합니다. 기존 로컬 메트릭과 대시보드는 자동 이전되지 않습니다.

## 대시보드 확인

기본 Kubernetes 대시보드는 설치 시 자동 등록됩니다. `Kubernetes / Compute Resources / Namespace (Pods)`에서 namespace를 `chatting-alarm`으로 선택하면 앱 Pod의 CPU·메모리 사용량을 볼 수 있습니다. 시간 범위는 `Last 15 minutes`로 시작합니다.

API 요청량·응답 시간과 JVM 메트릭은 별도 패널을 만듭니다. 새 대시보드를 생성하고 Prometheus 데이터 소스를 선택합니다. 기존 패널을 복사할 때는 `$namespace`, `$cluster`, `$datasource` 같은 변수도 함께 설정해야 합니다. 고정 namespace를 사용할 경우 쿼리에 `namespace="chatting-alarm"`을 지정하고 불필요한 변수 조건을 제거합니다.

CPU·메모리·재시작 패널은 `서버 자원`, 요청 수·오류·응답 시간은 `API 상태`로 묶습니다. 그룹은 대시보드 편집 화면의 `Group panels`로 구성합니다. 화면에서 만든 대시보드는 Grafana PVC에 저장되며 저장소에 자동 반영되지 않습니다. 재설치 후에도 사용하려면 JSON으로 내보내거나 provisioning 설정으로 관리합니다.

Explore에서 아래 쿼리로 백엔드 HTTP 요청량을 확인할 수 있습니다.

```promql
sum(rate(http_server_requests_seconds_count{
  namespace="chatting-alarm",
  service="chatting-alarm-backend",
  uri!~"/actuator.*"
}[$__rate_interval]))
```

WebSocket 메시지 수는 HTTP 요청 수와 다릅니다. 채팅 메시지 처리량·전달 지연은 별도 애플리케이션 계측이 필요합니다.

## 설치 오류 확인

| 증상 | 확인 및 조치 |
|---|---|
| `ebs.csi.aws.com not found` | Terraform 적용 후 EBS CSI 컨트롤러 상태를 확인합니다. |
| `serviceMonitorNamespaceSelector.matchNames` 스키마 오류 | 최신 `values.yaml`은 namespace 이름 라벨의 `matchExpressions`를 사용합니다. 파일을 갱신하고 설치 스크립트를 다시 실행합니다. ServiceMonitor의 `namespaceSelector.matchNames`는 다른 필드이므로 그대로 둡니다. |
| Helm 설치 실패 후 재시도 | 원인을 수정한 뒤 같은 `install.sh`를 실행합니다. 실패했다는 이유만으로 PVC나 릴리스를 삭제하지 않습니다. |
| 복사한 패널에 `No data` | 데이터 소스, 대시보드 변수, 쿼리 라벨과 시간 범위를 확인합니다. `up` 쿼리로 수집 상태도 확인합니다. |
| Grafana 접속 시간 초과 | 허용 CIDR과 현재 공인 IP를 비교하고 DNS가 Grafana ALB를 가리키는지 확인합니다. |
