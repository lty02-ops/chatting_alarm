# 모두톡

1인 개발로 진행한 Spring Boot기반 채팅 서비스 프로젝트입니다.

약 2개월간 진행했으며, Kubernetes 기반으로 배포 환경을 구축했습니다.

[![CI](https://github.com/lty02-ops/chatting_alarm/actions/workflows/ci.yml/badge.svg)](https://github.com/lty02-ops/chatting_alarm/actions/workflows/ci.yml)


## 주요 기능

- Google, Naver, Kakao 소셜 로그인 기능
- WebSocket/STOMP를 활용한 실시간 채팅 기능
- 1:1 채팅과 단체 채팅방 생성 기능
- 친구 코드를 이용해 요청을 보내고 상대방이 수락하는 친구 추가 방식
- 단체방에 참여자 초대가 가능하고 참여자 간 친구 요청 가능
- 온라인 상태와 현재 접속중인 이용자 목록 표시
- 읽지 않은 메세지 알림 제공
- 파일 첨부 및 다운로드, 이모지 첨부 기능
- 프로필 이름과 이미지 변경 기능

## 사용 기술

### App

![Java 17](https://img.shields.io/badge/Java_17-ED8B00?style=flat-square&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-6DB33F?style=flat-square&logo=springboot&logoColor=white)
![Spring Security](https://img.shields.io/badge/Spring_Security-6DB33F?style=flat-square&logo=springsecurity&logoColor=white)
![React](https://img.shields.io/badge/React-61DAFB?style=flat-square&logo=react&logoColor=black)
![Vite](https://img.shields.io/badge/Vite-646CFF?style=flat-square&logo=vite&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL_8-4479A1?style=flat-square&logo=mysql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-DC382D?style=flat-square&logo=redis&logoColor=white)

### Infra
![Docker](https://img.shields.io/badge/Docker-2496ED?style=flat-square&logo=docker&logoColor=white)
![Kubernetes](https://img.shields.io/badge/Kubernetes-326CE5?style=flat-square&logo=kubernetes&logoColor=white)
![Terraform](https://img.shields.io/badge/Terraform-844FBA?style=flat-square&logo=terraform&logoColor=white)
![GitHub Actions](https://img.shields.io/badge/GitHub_Actions-2088FF?style=flat-square&logo=githubactions&logoColor=white)
![AWS](https://img.shields.io/badge/AWS-232F3E?style=flat-square&logo=amazonwebservices&logoColor=white)


## 아키텍처

### 전체 구성

![모두톡 AWS 아키텍처](image/Chatting_alarm_overall.png)

외부 DNS에 프론트엔드와 백엔드 도메인을 각각 등록하고, Ingress의 규칙에 따라 ALB가 요청을 라우팅하도록 구성했습니다.

프론트엔드와 백엔드는 각각 2개의 Pod로 배포했고, 가용영역 간 분산 배치를 위해 TopoLogy Spread Constraint를 적용했습니다. 노드 유지보수 시에는 동시에 두 Pod이 중단되지 않도록 PodDisruptionBudget을 설정했습니다.

### CI/CD

![모두톡 CI/CD Pipeline](image/Chatting_alarm_cicd.png)

**CI** - `main` 브랜치에 새 코드가 푸쉬되거나 Pull Request가 생성되면 백엔드 테스트와 프론트엔드 빌드를 실행합니다.

**CD** - 배포는 GitHub Actions에서 수동으로 실행하고, GitHub OIDC를 통해 AWS IAM Role 임시 자격 증명을 발급받아 ECR 및 EKS 접근 권한을 부여받습니다. 이후 새로운 코드를 Docker 이미지로 빌드해서 ECR에 업로드하고 `kubectl set image`로 EKS Deployment의 컨테이너 이미지 주소를 변경합니다.
마지막으로 Kubernetes는 이 변경 사항을 감지하여 RollingUpdate 방식으로 새 Pod을 배포합니다.


### 프론트엔드 요청 흐름

![모두톡 Frontend Flow](image/Chatting_alarm_Frontend_Flow.png)

사용자가 app.chatting-alarm.p-e.kr에 접속하면 외부 DNS를 통해 ALB의 IP 주소를 조회하고 ALB로 HTTPS 요청을 보냅니다. ALB는 ACM 인증서를 사용해 TLS 연결을 종료한 뒤, Ingress 설정으로 만든 호스트 라우팅 규칙에 따라 Frontend Target Group을 선택합니다. 이후 등록된 두 개의 React Pod 중 정상 상태인 Pod 중 하나로 요청을 전달합니다.

### 백엔드 REST API 요청 흐름

![모두톡 Backend REST API Flow](image/Chatting_alarm_Backend_REST_API_Flow.png)

사용자가 `api.chatting-alarm.p-e.kr`로 REST API를 요청하면 외부 DNS를 통해 ALB의 IP 주소를 조회하고 ALB로 HTTPS 요청을 보냅니다. ALB는 ACM 인증서를 사용해 TLS 연결을 종료한 뒤, Ingress 설정으로 만든 호스트 라우팅 규칙에 따라 Backend Target Group을 선택합니다. 이후 등록된 두 개의 Spring Boot Pod 중 정상 상태인 Pod 하나로 요청을 전달합니다. 애플리케이션은 Redis를 이용해 실시간 이벤트를 전달하고 사용자·친구·채팅방·메세지 등의 데이터는 RDS MySQL에 저장합니다.

### 백엔드 WebSocket 요청 흐름

![모두톡 Backend WebSocket Flow](image/Chatting_alarm_Backend_WebSocket_Flow.png)

브라우저가 api.chatting-alarm.p-e.kr/ws로 WSS 연결을 요청하면 외부 DNS를 통해 ALB의 IP 주소를 조회하고 ALB에 연결합니다. ALB는 ACM 인증서를 사용해 TLS 연결을 종료한 뒤, Ingress 설정으로 만든 호스트 및 경로 라우팅 규칙에 따라 Backend Target Group을 선택합니다. 이후 등록된 Spring Boot Pod 중 정상 상태인 Pod 하나로 연결을 전달합니다. 각 Pod는 Redis Pub/Sub을 이용해 채팅 메세지를 공유하고 접속 상태는 Redis에서 관리하며 채팅 메세지는 RDS MySQL에 저장합니다.

## 배포 및 실행 방법

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

### 3) AWS 배포

AWS 자격 증명과 Terraform S3 backend를 설정하고, [배포 가이드](docs/deployment.md)에 따라 인프라 생성, 시크릿 업로드와 ECR 이미지 준비를 완료합니다. 이후 Bash/WSL 환경에서 업로드한 이미지 태그를 지정해 실행합니다.

```bash
export IMAGE_TAG="$(git rev-parse HEAD)"
aws eks update-kubeconfig --name chatting_alarm-eks --region ap-northeast-2
bash infra/kubernetes/apply.sh
kubectl get pods,ingress -n chatting-alarm
```

Ingress의 `ADDRESS`를 DNS의 `app`, `api` CNAME 대상으로 등록합니다. Google·Kakao·Naver 개발자 콘솔에는 다음 Callback URL을 각각 등록합니다.

```text
https://api.chatting-alarm.p-e.kr/login/oauth2/code/google
https://api.chatting-alarm.p-e.kr/login/oauth2/code/kakao
https://api.chatting-alarm.p-e.kr/login/oauth2/code/naver
```

### 4) 서비스 실행

배포 후 [서비스 접속 주소](https://app.chatting-alarm.p-e.kr)에 접속하고 Google, Kakao 또는 Naver로 로그인합니다.

### 5) 변경 사항 배포

배포 가이드에 따라 GitHub Actions 변수 `AWS_DEPLOY_ROLE_ARN`을 등록한 뒤, 코드 변경 후 `main`에 push하고 **Build and deploy → Run workflow → main**을 선택합니다.

인프라, 환경 변수 또는 도메인이 바뀌면 Terraform, 시크릿과 DNS 설정도 별도로 갱신합니다.

## Trouble Shooting

### AWS Load Balancer Controller 시작 실패

| &nbsp;구&#8288;분&nbsp; | 내용 |
|---|---|
| &nbsp;문&#8288;제&nbsp; | ALB Controller 실행 시 `failed to get VPC ID ec2imds: GetMetadata context deadline exceeded` 오류가 발생했습니다. |
| &nbsp;원&#8288;인&nbsp; | EC2 Instance Metadata(IMDS) 요청이 시간 초과되어 Controller가 실행에 필요한 VPC ID를 가져오지 못했습니다. |
| &nbsp;해&#8288;결&nbsp; | `apply.sh`에서 Terraform의 `vpc_id` output을 읽고, `--aws-vpc-id=${VPC_ID}` 옵션으로 Controller에 직접 전달했습니다. |

### GitHub Actions OIDC 인증 실패

| &nbsp;구&#8288;분&nbsp; | 내용 |
|---|---|
| &nbsp;문&#8288;제&nbsp; | GitHub Actions 배포 과정에서 AWS IAM Role 인증에 실패했습니다. |
| &nbsp;원&#8288;인&nbsp; | IAM Trust Policy의 `sub` 조건이 실제 GitHub OIDC 토큰의 값과 일치하지 않았습니다. |
| &nbsp;해&#8288;결&nbsp; | GitHub API의 `sub_claim_prefix`를 조회하고 `:ref:refs/heads/main`을 붙여 Trust Policy에 반영했습니다. 현재 저장소는 `repo:lty02-ops@246391481/chatting_alarm@1126997863:ref:refs/heads/main`을 사용합니다. Terraform 변경을 적용한 뒤 배포를 재실행했습니다. |

### EKS에서 S3 업로드 실패

| &nbsp;구&#8288;분&nbsp; | 내용 |
|---|---|
| &nbsp;문&#8288;제&nbsp; | EKS의 Backend Pod에서 AWS 임시 자격 증명을 가져오지 못해 S3 파일 업로드에 실패했습니다. |
| &nbsp;원&#8288;인&nbsp; | S3 SDK는 있었지만 STS SDK 의존성이 없어, IRSA의 Web Identity Token을 임시 자격 증명으로 교환하지 못했습니다. |
| &nbsp;해&#8288;결&nbsp; | `pom.xml`에 AWS STS SDK 의존성을 추가한 뒤, IRSA를 통한 S3 파일 업로드가 정상적으로 동작하는 것을 확인했습니다. |

### RollingUpdate 중 Pod Pending 발생

| &nbsp;구&#8288;분&nbsp; | 내용 |
|---|---|
| &nbsp;문&#8288;제&nbsp; | 두 개의 `t3.small` 노드에서 배포를 진행할 때 새 Pod가 `Pending` 상태에 머물렀습니다. |
| &nbsp;원&#8288;인&nbsp; | 기존 Pod와 새 Pod를 동시에 실행할 노드 자원이 부족했습니다. |
| &nbsp;해&#8288;결&nbsp; | 노드를 `t3.medium`으로 변경하고, 프론트엔드의 RollingUpdate 전략을 `maxUnavailable: 1`, `maxSurge: 0`으로 설정했습니다. 기존 Pod 하나를 먼저 종료해 자원을 확보한 뒤 새 Pod를 배포하도록 했습니다. |

## 배운 점
- **Kubernetes 배포:** ALB와 Ingress를 활용한 요청 라우팅과 Pod 분산 배치 방법을 이해했습니다.
- **Redis Pub/Sub:** 서로 다른 Pod에 연결된 사용자 간 채팅 메세지를 공유하는 방법을 배웠습니다.
- **CI/CD 구성:** GitHub Actions와 OIDC를 활용해 AWS 인증과 배포 과정을 구성하는 경험을 했습니다.

## 아쉬운 점

- **성능 검증 부족:** 여러 사용자가 동시에 채팅하는 상황을 테스트하지 못했습니다. 이후에 부하 테스트를 진행하여 서버 자원 사용량이나 메세지 지연 시간 등을 확인하고 싶습니다.

- **운영 경험 부족:** 실제 운영 환경에서 장기간 서비스를 운영해보지 못한 점이 아쉽습니다. 운영 중 발생할 수 있는 장애 상황 등에 대한 경험을 하며 서비스의 안정성을 높이고 싶습니다.

## 개발자

- GitHub: [lty02-ops](https://github.com/lty02-ops)
