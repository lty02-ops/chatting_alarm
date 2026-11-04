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

이 저장소에서는 `github_repository = "lty02-ops/chatting_alarm"`로 설정합니다. 다른 저장소를 사용한다면 `github-oidc.tf`의 신뢰 정책도 해당 저장소의 OIDC subject에 맞춰 변경해야 합니다.

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

GitHub **Settings → Secrets and variables → Actions → Variables**에 `AWS_DEPLOY_ROLE_ARN`을 등록합니다. 값은 다음 명령의 출력입니다.

```bash
terraform -chdir=infra/aws output -raw github_actions_role_arn
```

코드를 `main`에 push한 뒤 **Actions → Build and deploy → Run workflow → main**으로 배포합니다. CI는 자동 실행되지만 배포는 수동입니다.

워크플로는 커밋 SHA 태그로 이미지를 빌드하고 기존 Deployment를 갱신합니다. 초기 Kubernetes 리소스 생성, Terraform 변경, 시크릿 저장과 DNS 수정은 별도로 진행합니다. `apply.sh`를 다시 실행할 때도 실제 ECR에 존재하는 `IMAGE_TAG`를 지정합니다.


## 배포 상태 확인 및 복구

### 인프라 재생성 후 접속 또는 배포 실패

| 증상 | 확인 및 조치 |
|---|---|
| `DNS_PROBE_FINISHED_NXDOMAIN` | `kubectl get ingress -n chatting-alarm`의 ALB 주소와 DNS CNAME을 비교합니다. Ingress가 없으면 Kubernetes 초기 배포부터 진행합니다. |
| `FailedMount`, 시크릿의 `AWSCURRENT` 없음 | `.env` 값을 업로드하고 Secrets Manager의 버전 레이블을 확인합니다. `.env` 수정만으로 EKS에 전달되지는 않습니다. |
| `ImagePullBackOff`, `latest: not found` | ECR에 실제 존재하는 태그를 지정합니다. GitHub Actions의 기본 이미지 태그는 커밋 SHA입니다. |
| 배포 `progress deadline` 초과 | Pod 이벤트에서 원인을 해결한 뒤 필요하면 `kubectl rollout restart deployment/chatting-alarm-backend -n chatting-alarm`으로 재시작합니다. 대기 시간 초과만으로 원인을 판단하지 않습니다. |

```bash
kubectl get pods -n chatting-alarm -o wide
kubectl get events -n chatting-alarm --sort-by=.metadata.creationTimestamp | tail -30
kubectl logs -n chatting-alarm -l app=chatting-alarm-backend -c backend --tail=100
```
