# AWS 배포 가이드

아래 명령은 저장소 루트의 Bash/WSL에서 실행합니다. AWS CLI, Terraform 1.10 이상, 클러스터 버전에 맞는 kubectl, Docker, Python 3, `envsubst`가 필요합니다. Docker daemon도 실행되어 있어야 합니다. 모니터링에는 Helm 3를 추가로 사용합니다.

기본 설정은 서울 리전(`ap-northeast-2`), `dev` 환경, `chatting_alarm-eks` 클러스터입니다. 시크릿 업로드 스크립트와 Jenkins 설정도 이 값을 사용합니다. 리전·환경·도메인을 바꾸면 관련 스크립트와 Kubernetes 설정도 함께 변경해야 합니다.

최초 배포는 1~5단계, 기존 앱의 코드 변경은 6단계를 따릅니다. 전체 인프라를 삭제한 뒤 다시 배포한다면 [삭제 후 재배포](#삭제-후-재배포)를 먼저 확인합니다. 배포 시 AWS 비용이 발생합니다.

### 1) Repository Clone

```bash
git clone https://github.com/lty02-ops/chatting_alarm.git
cd chatting_alarm
```

### 2) 환경 변수 설정

프로젝트 루트의 `.env.example`을 `.env`로 복사하고 Google·Kakao·Naver 로그인 정보를 입력합니다.

```bash
cp .env.example .env
```

```dotenv
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
KAKAO_CLIENT_ID=
KAKAO_CLIENT_SECRET=
NAVER_CLIENT_ID=
NAVER_CLIENT_SECRET=
```

### 3) AWS 인프라 생성

먼저 AWS 계정과 Docker 연결을 확인합니다. 여러 AWS 프로필을 사용한다면 `AWS_PROFILE`을 지정한 뒤 같은 세션에서 진행합니다.

```bash
aws sts get-caller-identity
aws configure list
docker info
```

처음 설정할 때만 예제를 복사하고 `bucket_suffix` 등을 수정합니다. 기존 `terraform.tfvars`가 있다면 그대로 사용합니다.

```bash
cp infra/aws/terraform.tfvars.example infra/aws/terraform.tfvars
```

`infra/aws/backend.tf`에 지정한 상태 저장 버킷은 Terraform 초기화 전에 있어야 합니다. 기존 버킷과 state가 있으면 재사용합니다. 조회되지 않으면 계정과 권한부터 확인합니다. 기존 state를 빈 버킷으로 대체하지 않습니다.

```bash
aws s3api head-bucket --bucket chatting-alarm-terraform-state-lty02
```

새 환경에서 버킷이 없는 경우에만 생성합니다. 다른 계정에서 배포한다면 고유한 이름을 정하고 `backend.tf`에도 반영합니다.

```bash
aws s3api create-bucket \
  --bucket chatting-alarm-terraform-state-lty02 \
  --region ap-northeast-2 \
  --create-bucket-configuration LocationConstraint=ap-northeast-2

aws s3api put-bucket-versioning \
  --bucket chatting-alarm-terraform-state-lty02 \
  --versioning-configuration Status=Enabled
```

Jenkins 배포 시 아래 [Jenkins 설정](#jenkins-설정)에 따라 에이전트의 AWS 인증과 권한을 준비합니다. GitHub Actions를 사용하지 않는 신규 환경에서는 `github_repository = null`로 설정합니다.

```bash
terraform -chdir=infra/aws init
terraform -chdir=infra/aws plan
terraform -chdir=infra/aws apply
terraform -chdir=infra/aws output acm_dns_validation_records
```

출력된 인증서 검증용 CNAME을 DNS에 등록하고 발급 상태를 확인합니다. 기존 검증 레코드가 남아 있다면 출력값과 일치하는지 확인합니다.

```bash
CERTIFICATE_ARN="$(terraform -chdir=infra/aws output -raw acm_certificate_arn)"
aws acm describe-certificate \
  --certificate-arn "$CERTIFICATE_ARN" \
  --region ap-northeast-2 \
  --query Certificate.Status --output text
```

`ISSUED`가 된 뒤 진행합니다. `apply.sh`는 인증서가 미발급 상태이면 중단됩니다. Terraform은 EKS, RDS, Redis, ECR, EBS CSI 드라이버 등을 생성하며 앱은 다음 단계에서 배포합니다.

### 4) 시크릿 업로드 및 이미지 빌드

작성한 `.env`를 AWS Secrets Manager에 업로드합니다. 여섯 개의 OAuth 값이 모두 있어야 합니다. EKS는 `.env`를 직접 읽지 않습니다.

```bash
python3 infra/aws/upload-backend-env.py
```

최초 배포용 이미지를 ECR에 올립니다. 두 이미지가 이미 있다면 이 과정은 생략하고 해당 태그를 `IMAGE_TAG`로 지정합니다.

```bash
export IMAGE_TAG="$(git rev-parse HEAD)"
BACKEND_REPO="$(terraform -chdir=infra/aws output -raw backend_ecr_repository_url)"
FRONTEND_REPO="$(terraform -chdir=infra/aws output -raw frontend_ecr_repository_url)"
REGISTRY="${BACKEND_REPO%%/*}"
aws ecr get-login-password --region ap-northeast-2 | docker login --username AWS --password-stdin "$REGISTRY"
docker build --platform linux/amd64 -t "$BACKEND_REPO:$IMAGE_TAG" backend
docker push "$BACKEND_REPO:$IMAGE_TAG"
docker build --platform linux/amd64 --build-arg VITE_API_BASE_URL=https://api.chatting-alarm.p-e.kr -t "$FRONTEND_REPO:$IMAGE_TAG" frontend
docker push "$FRONTEND_REPO:$IMAGE_TAG"
```

### 5) Kubernetes 배포 및 서비스 접속

이미지를 업로드한 Bash/WSL 세션에서 배포 스크립트를 실행합니다. 새 세션에서는 ECR에 업로드한 `IMAGE_TAG`를 다시 지정합니다. 커밋 SHA를 지정하는 것만으로 이미지가 만들어지지는 않습니다.

```bash
aws eks update-kubeconfig --name chatting_alarm-eks --region ap-northeast-2
: "${IMAGE_TAG:?업로드한 이미지 태그를 지정하세요}"
IMAGE_TAG="$IMAGE_TAG" bash infra/kubernetes/apply.sh
kubectl get pods,ingress -n chatting-alarm
```

백엔드·프론트엔드가 각각 두 개씩 `1/1 Running`인지 확인하고, Ingress의 `ADDRESS`를 DNS의 `app`, `api` CNAME 대상으로 등록합니다. 인프라를 재생성했다면 새 ALB 주소로 변경해야 합니다.

Google·Kakao·Naver 개발자 콘솔에 다음 Callback URL을 각각 등록합니다.

```text
https://api.chatting-alarm.p-e.kr/login/oauth2/code/google
https://api.chatting-alarm.p-e.kr/login/oauth2/code/kakao
https://api.chatting-alarm.p-e.kr/login/oauth2/code/naver
```

배포 후 **https://app.chatting-alarm.p-e.kr**에 접속하고 Google, Kakao 또는 Naver로 로그인합니다. 다른 도메인을 사용한다면 인프라 설정, 프론트엔드 이미지의 API 주소와 Callback URL도 해당 도메인으로 변경합니다.

### 모니터링 설치

앱 배포와 DNS 연결을 마쳤다면 [모니터링 가이드](monitoring.md)에 따라 Prometheus와 Grafana를 설치합니다. 앱의 `apply.sh`는 모니터링을 설치하지 않습니다. `grafana` DNS에는 앱 ALB가 아닌 Grafana Ingress 주소를 등록합니다.

### 6) 변경 사항 배포

아래 [Jenkins 설정](#jenkins-설정)에 따라 자동 검증 작업과 별도 수동 배포 작업을 생성합니다. 배포 에이전트의 AWS 인증은 다음 명령으로 확인합니다.

```bash
aws sts get-caller-identity
```

코드를 `main`에 push한 뒤 Jenkins 수동 배포 작업에서 **Build Now**로 배포합니다. CI는 자동 실행되지만 배포는 수동입니다.

배포 파이프라인은 커밋 SHA 태그로 이미지를 빌드하고 기존 Deployment를 갱신합니다. 초기 Kubernetes 리소스 생성, Terraform 변경, 시크릿 저장과 DNS 수정은 별도로 진행합니다. `apply.sh`를 다시 실행할 때도 실제 ECR에 존재하는 `IMAGE_TAG`를 지정합니다.


## Jenkins 설정

Jenkins에 Pipeline, Git, GitHub Branch Source, JUnit 플러그인을 설치합니다. Jenkins 서버와 에이전트 IAM Role 생성은 이 저장소의 Terraform에 포함되지 않습니다.

| 에이전트 라벨 | 필요한 도구 |
|---|---|
| `chatting-alarm-ci` | Linux, Git, Java 17, Maven 3, Node.js 22, npm |
| `chatting-alarm-deploy` | 위 도구 + Bash, Docker CLI와 Docker daemon, AWS CLI v2, 클러스터 버전에 맞는 kubectl |

도구는 에이전트 PATH에 설치하고 npm/Maven 저장소, ECR, STS, EKS API 접근을 허용합니다. 배포 이미지는 `linux/amd64`로 빌드합니다.

### AWS 권한

배포 에이전트에서 AWS CLI 기본 자격 증명 체인을 사용할 수 있도록 전용 AWS 프로필 또는 IAM Role을 연결합니다. EC2에서는 인스턴스 프로파일, EKS에서는 IRSA 등을 사용할 수 있습니다. 키를 저장소나 Docker 이미지에 포함하지 않습니다.

배포에 사용하는 IAM 주체에는 두 ECR 저장소의 이미지 업로드 권한과 EKS 조회 권한이 필요합니다. EKS access entry와 `chatting-alarm` 네임스페이스의 Edit 권한도 별도로 준비해야 합니다. 이 권한 설정은 현재 저장소에서 자동 생성하지 않습니다. 배포 에이전트에서 `aws sts get-caller-identity`로 인증을 확인합니다. 파이프라인은 ECR과 EKS가 인증된 AWS 계정에 있다고 가정합니다.

검증 에이전트에는 배포 권한을 부여하지 않습니다. 배포 에이전트는 신뢰하는 `main` 배포 작업 전용으로 운영하고, PR 작업이 실행되지 않도록 Jenkins 권한과 노드 설정으로 제한합니다. 라벨만으로 접근을 통제하지 않으며 컨트롤러에서는 빌드하지 않습니다.

### CI 에이전트 이미지

`infra/jenkins/ci-agent/Dockerfile`에는 Java 17, Maven 3.9, Node.js 22, npm, Git이 포함됩니다. Jenkins 연결 프로세스는 별도의 Java 21로 실행됩니다. 저장소 루트에서 이미지를 빌드합니다.

```bash
docker build -t chatting-alarm-ci-agent:local infra/jenkins/ci-agent
```

Jenkins 노드를 이름과 라벨 `chatting-alarm-ci`, Remote root directory `/home/jenkins/agent`, inbound/WebSocket 연결 방식으로 등록합니다. 실행 중인 빌드가 없을 때 기존 기본 에이전트 컨테이너를 중지·삭제하고 새 이미지로 교체합니다. 아래 볼륨은 유지합니다.

```bash
docker stop chatting-alarm-ci-agent
docker rm chatting-alarm-ci-agent
read -rsp "Jenkins node secret: " JENKINS_SECRET; echo
docker run -d --init \
  --name chatting-alarm-ci-agent \
  --restart unless-stopped \
  --add-host host.docker.internal:host-gateway \
  -e JENKINS_URL=http://host.docker.internal:8082/ \
  -e JENKINS_SECRET="$JENKINS_SECRET" \
  -e JENKINS_AGENT_NAME=chatting-alarm-ci \
  -e JENKINS_WEB_SOCKET=true \
  -e JENKINS_AGENT_WORKDIR=/home/jenkins/agent \
  -v chatting_alarm_ci_agent:/home/jenkins/agent \
  chatting-alarm-ci-agent:local
unset JENKINS_SECRET
docker logs -f chatting-alarm-ci-agent
```

`8082`는 호스트에 공개한 Jenkins 포트에 맞춥니다. `Connected`를 확인한 뒤 `Ctrl+C`로 로그 화면을 나옵니다. 컨테이너는 계속 실행됩니다. Jenkins의 `main` 작업에서 Build Now로 CI를 실행합니다. 이 이미지는 CI 전용이며 배포용 Docker/AWS CLI/kubectl은 포함하지 않습니다.

### 자동 검증 작업

1. **Multibranch Pipeline** 작업을 만들고 GitHub Branch Source에 저장소를 연결합니다. Script Path는 `Jenkinsfile`입니다.
2. Branch/PR discovery를 설정하고 `main`과 대상 브랜치가 `main`인 PR만 빌드하도록 필터링합니다. 포크 PR trust 정책은 신뢰하는 작성자로 제한합니다.
3. GitHub webhook을 `https://<jenkins-host>/github-webhook/`에 연결하고 브랜치 검색을 실행합니다.
4. 첫 검증 후 GitHub 브랜치 보호의 기존 `backend-test`/`frontend-build` 필수 검사를 실제 Jenkins 상태 검사 이름으로 교체합니다.

### 수동 배포 작업

먼저 `infra/jenkins/deploy-agent/Dockerfile`로 전용 에이전트 이미지를 준비합니다. CI 이미지 `chatting-alarm-ci-agent:local`을 먼저 빌드해야 하며 AWS CLI v2, Docker CLI/Buildx, kubectl이 추가됩니다. kubectl은 EKS 버전에 맞추어 빌드 시 지정합니다.

```bash
EKS_VERSION=$(aws eks describe-cluster --name chatting_alarm-eks --region ap-northeast-2 --query cluster.version --output text) || exit 1
KUBECTL_VERSION=$(curl -fsSL "https://dl.k8s.io/release/stable-${EKS_VERSION}.txt") || exit 1
docker build --build-arg KUBECTL_VERSION="$KUBECTL_VERSION" \
  -t chatting-alarm-deploy-agent:local infra/jenkins/deploy-agent
```

Jenkins에 이름과 라벨 `chatting-alarm-deploy`, Remote root directory `/home/jenkins/agent`, inbound/WebSocket 방식의 전용 노드를 등록합니다. 컨테이너에는 Jenkins 접속 설정, 별도 작업 볼륨, Docker daemon 접근, 배포용 AWS 인증을 연결해야 합니다. 로컬 Docker Desktop의 `/var/run/docker.sock`을 연결할 경우 호스트의 Docker 제어 권한이 생기므로 신뢰하는 배포 작업만 이 노드에 할당합니다. 일반 사용자로 실행할 때는 소켓의 그룹 ID도 `--group-add`로 전달합니다. AWS 인증 방식은 사용 중인 프로필 또는 IAM Role에 맞춰 설정합니다. 이 파일 추가만으로 인증이나 노드 연결이 완료되지는 않습니다.

1. 별도의 **Pipeline** 작업 하나를 만들고 **Pipeline script from SCM**에서 Git 저장소를 연결합니다.
2. Branch Specifier는 `*/main`, remote 이름은 `origin`, Script Path는 `Jenkinsfile.deploy`로 고정합니다. 임의 브랜치/ref 파라미터는 추가하지 않습니다.
3. webhook, Poll SCM, 주기 실행 등 자동 트리거를 끄고 실행·설정 권한은 배포 담당자로 제한합니다.
4. 초기 Kubernetes 배포 후 **Build Now**로 실행합니다. 같은 환경을 배포하는 작업은 하나만 운영합니다.

배포 작업은 체크아웃한 커밋이 `origin/main`인지 확인하고 테스트·프론트 빌드를 재검증합니다. 커밋 SHA 태그로 이미지를 업로드하고 두 Deployment를 갱신한 뒤 각각 최대 5분 동안 rollout을 확인합니다. 동시 배포는 직렬화하며 임시 Docker 인증 파일과 kubeconfig는 종료 시 삭제합니다.

기존 GitHub Actions 배포에서 전환한 경우, Jenkins 배포 확인 후 `github_repository = null`로 변경합니다. Terraform plan에서 이전 GitHub OIDC Role/Provider/access entry 삭제를 확인한 뒤 적용하고 사용하지 않는 GitHub Actions 인증 설정을 정리합니다.

한쪽 Deployment만 갱신된 상태에서 실패할 수 있으며 자동 롤백하지 않습니다. 실패하면 양쪽 상태를 확인하고 필요 시 이전 정상 SHA로 각각 `kubectl set image`한 뒤 rollout을 확인합니다.

## 배포 상태 확인 및 복구

### 인프라 재생성 후 접속 또는 배포 실패

| 증상 | 확인 및 조치 |
|---|---|
| `DNS_PROBE_FINISHED_NXDOMAIN` | `kubectl get ingress -n chatting-alarm`의 ALB 주소와 DNS CNAME을 비교합니다. Ingress가 없으면 Kubernetes 초기 배포부터 진행합니다. |
| `FailedMount`, 시크릿의 `AWSCURRENT` 없음 | `.env` 값을 업로드하고 Secrets Manager의 버전 레이블을 확인합니다. `.env` 수정만으로 EKS에 전달되지는 않습니다. |
| `ImagePullBackOff`, `latest: not found` | ECR에 실제 존재하는 태그를 지정합니다. Jenkins 배포의 이미지 태그는 커밋 SHA입니다. |
| 배포 `progress deadline` 초과 | Pod 이벤트에서 원인을 해결한 뒤 필요하면 `kubectl rollout restart deployment/chatting-alarm-backend -n chatting-alarm`으로 재시작합니다. 대기 시간 초과만으로 원인을 판단하지 않습니다. |

```bash
kubectl get pods -n chatting-alarm -o wide
kubectl get events -n chatting-alarm --sort-by=.metadata.creationTimestamp | tail -30
kubectl logs -n chatting-alarm -l app=chatting-alarm-backend -c backend --tail=100
```

## 삭제 후 재배포

상태 저장 버킷과 기존 Terraform 설정은 유지합니다. 인프라를 삭제했다면 3단계부터 다시 생성하고, `.env` 업로드와 이미지 빌드·업로드도 다시 진행합니다. 앱과 Grafana ALB 주소가 바뀌므로 DNS 대상도 갱신합니다. 삭제된 DB·파일·대시보드 데이터는 이 과정으로 복구되지 않습니다.

### 시크릿 이름이 삭제 대기 중인 경우

백엔드 시크릿은 삭제 시 7일간 복구 기간을 둡니다. 이 기간에 같은 이름으로 생성하면 충돌할 수 있습니다. 인프라 생성 전에 확인합니다.

```bash
aws secretsmanager describe-secret \
  --secret-id chatting-alarm/dev/backend \
  --region ap-northeast-2 \
  --query '{ARN:ARN,DeletedDate:DeletedDate}'
```

`ResourceNotFoundException`이면 새로 생성할 수 있습니다. `DeletedDate`가 있으면 삭제 예약을 취소합니다. Terraform 초기화와 변수 설정 후, state에 해당 리소스가 없는 경우에만 import합니다.

```bash
aws secretsmanager restore-secret \
  --secret-id chatting-alarm/dev/backend \
  --region ap-northeast-2

SECRET_ARN="$(aws secretsmanager describe-secret \
  --secret-id chatting-alarm/dev/backend \
  --region ap-northeast-2 --query ARN --output text)"

terraform -chdir=infra/aws state list
# 위 목록에 aws_secretsmanager_secret.backend가 없을 때만 실행
terraform -chdir=infra/aws import aws_secretsmanager_secret.backend "$SECRET_ARN"
```

이후 `terraform plan`을 확인하고 배포를 이어갑니다. 복구한 시크릿도 4단계에서 현재 `.env` 값으로 다시 업로드합니다. 삭제 예약 취소는 [AWS restore-secret 문서](https://docs.aws.amazon.com/cli/latest/reference/secretsmanager/restore-secret.html)를 참고하세요.
