# AWS 배포 가이드

Bash/WSL에서 AWS CLI, Terraform 1.10 이상, kubectl, Docker, Python 3, `envsubst`가 필요합니다. AWS 자격 증명과 인프라 생성·ECR 업로드·EKS 접근 권한을 준비하고, 아래 명령은 저장소 루트에서 실행합니다.

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

`infra/aws/terraform.tfvars.example`을 참고해 `terraform.tfvars`를 작성하고, `backend.tf`의 S3 상태 저장 버킷을 준비합니다. 기존 인프라를 갱신할 때는 기존 설정과 state를 사용합니다.

Jenkins 배포 시 아래 [Jenkins 설정](#jenkins-설정)에 따라 에이전트의 AWS 인증과 권한을 준비합니다. GitHub Actions를 사용하지 않는 신규 환경에서는 `github_repository = null`로 설정합니다.

```bash
terraform -chdir=infra/aws init
terraform -chdir=infra/aws plan
terraform -chdir=infra/aws apply
terraform -chdir=infra/aws output acm_dns_validation_records
```

출력된 인증서 검증용 CNAME을 DNS에 등록하고 ACM 상태가 `ISSUED`인지 확인합니다. Terraform은 AWS 인프라만 생성하며 앱은 다음 단계에서 배포합니다.

### 4) 시크릿 업로드 및 이미지 빌드

작성한 `.env`를 AWS Secrets Manager에 업로드합니다. EKS는 `.env`를 직접 읽지 않습니다.

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

이미지를 업로드한 Bash/WSL 세션에서 배포 스크립트를 실행합니다. `IMAGE_TAG`는 앞 단계에서 업로드한 이미지 태그를 사용합니다.

```bash
aws eks update-kubeconfig --name chatting_alarm-eks --region ap-northeast-2
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

Jenkins 배포 확인 후 `github_repository = null`로 변경하고 Terraform plan에서 이전 GitHub OIDC Role/Provider/access entry 삭제를 검토한 뒤 적용합니다. 기존 `github-oidc.tf`의 로컬 수정은 보존했습니다. GitHub Actions 변수 `AWS_DEPLOY_ROLE_ARN` 등 사용하지 않는 배포 인증 설정도 정리합니다.

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
