# 모두톡

1인 개발로 진행한 Spring Boot기반 채팅 서비스 프로젝트입니다.

약 2개월간 진행했으며, Kubernetes 기반으로 배포 환경을 구축했습니다.

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
![Jenkins](https://img.shields.io/badge/Jenkins-D24939?style=flat-square&logo=jenkins&logoColor=white)
![AWS](https://img.shields.io/badge/AWS-232F3E?style=flat-square&logo=amazonwebservices&logoColor=white)
![Bash](https://img.shields.io/badge/Bash-4EAA25?style=flat-square&logo=gnubash&logoColor=white)

### Monitoring
![Prometheus](https://img.shields.io/badge/Prometheus-E6522C?style=flat-square&logo=prometheus&logoColor=white)
![Grafana](https://img.shields.io/badge/Grafana-F46800?style=flat-square&logo=grafana&logoColor=white)


## 아키텍처

### 전체 아키텍쳐

![모두톡 AWS 아키텍처](image/Chatting_alarm_overall.png)

**서비스 접근 / 트래픽 라우팅**

외부 DNS에 프론트엔드, 백엔드, Grafana 도메인을 각각 등록하고 앱 ALB는 Ingress의 규칙에 따라 프론트엔드와 백엔드 Pod로 요청을 전달합니다. Grafana는 별도 ALB를 통해 지정한 관리자 IP에서만 접근이 가능하도록 구성했습니다.

**Pod 배치 / 가용성**

프론트엔드와 백엔드는 각각 2개의 Pod로 배포했고 하나의 가용영역에 집중되지 않도록 분산 배치했습니다. 노드는 유지보수 중에 서비스가 계속 실행될 수 있게 각 서비스의 Pod를 최소 1개씩은 유지할 수 있도록 설정했습니다.


**모니터링 / 로그 수집**

Prometheus로 백엔드와 Kubernetes 메트릭을 수집하고 Grafana에서 이를 시각화합니다. 모니터링 데이터는 EBS에 저장하고 CloudWatch는 로그 수집과 AWS 리소스 경보 알림에 사용합니다.

### CI / CD

![모두톡 CI/CD Pipeline](image/Chatting_alarm_cicd.png)

**CI** - `main` 브랜치에 새 코드가 push되거나 Pull Request가 생성되면 Jenkins에서 백엔드 테스트와 프론트엔드 빌드를 실행합니다.

**CD** - `deploy-dev.sh`가 Jenkins 배포 작업을 실행합니다. AWS 인증 후 새로운 코드를 Docker 이미지로 빌드해서 ECR에 업로드하고 Kubernetes 설정에 새 이미지 태그를 적용해 EKS에 배포합니다.
마지막으로 Kubernetes는 이 변경 사항을 감지하여 RollingUpdate 방식으로 새 Pod을 배포합니다.


### 프론트엔드 요청 흐름

![모두톡 Frontend Flow](image/Chatting_alarm_Frontend_Flow.png)


**접속 / 요청 전달**

사용자가 app.chatting-alarm.p-e.kr에 접속하면 외부 DNS를 통해 ALB의 IP 주소를 확인하고 HTTPS 요청을 보냅니다.
ALB는 ACM 인증서를 사용해 TLS 연결을 종료한 뒤에 Ingress 설정으로 만든 호스트 라우팅 규칙에 따라 Frontend Target Group으로 요청을 전달합니다.

**프론트엔드 응답**

ALB는 Target Group에 등록된 두 개의 React Pod 중 정상인 Pod 하나로 요청을 전달합니다.

### 백엔드 REST API 요청 흐름

![모두톡 Backend REST API Flow](image/Chatting_alarm_Backend_REST_API_Flow.png)

**API 요청 / 전달**

브라우저가 `api.chatting-alarm.p-e.kr`로 REST API를 요청하면 외부 DNS를 통해 ALB의 IP 주소를 확인하고 ALB로 HTTPS 요청을 보냅니다. 이후 ACM 인증서를 사용해 TLS 연결을 종료한 뒤에 Ingress 설정으로 만든 호스트 라우팅 규칙에 따라 Backend Target Group을 선택하고 등록된 두 개의 Spring Boot Pod 중 정상인 Pod 하나로 요청을 전달합니다.

**데이터 처리 / 저장**

Redis는 실시간 알림과 메세지를 전달하는데 사용하고 사용자, 친구, 채팅방, 메세지 등의 데이터는 RDS MySQL에 저장합니다.

### 백엔드 WebSocket 요청 흐름

![모두톡 Backend WebSocket Flow](image/Chatting_alarm_Backend_WebSocket_Flow.png)


**실시간 연결**

브라우저가 api.chatting-alarm.p-e.kr/ws로 WSS 연결을 요청하면 외부 DNS를 통해 ALB의 IP 주소를 확인하고 ALB에 연결합니다. ALB는 ACM 인증서를 사용해 TLS 연결을 종료한 뒤에 Ingress 설정으로 만든 호스트 및 경로 라우팅 규칙에 따라 Backend Target Group을 선택하고 등록된 Spring Boot Pod 중 정상 Pod 하나로 연결을 전달합니다.

**메세지 공유 / 저장**

각 Pod는 Redis Pub/Sub을 이용해 채팅 메세지를 공유하고 접속 상태는 Redis에서 관리하며 채팅 메세지는 RDS MySQL에 저장합니다.

## 배포 및 실행 방법

### 1. Repository Clone

```bash
git clone https://github.com/lty02-ops/chatting_alarm.git
cd chatting_alarm
```

### 2. 환경 설정

```bash
cp -n .env.example .env
cp -n infra/aws/terraform.tfvars.example infra/aws/terraform.tfvars
```

`.env`에 Google, Kakao, Naver OAuth 값과 Jenkins URL·사용자명·API 토큰을 입력하고 AWS 인증과 배포 설정을 준비합니다.

최초 실행 전 Jenkins 노드의 Labels에 `chatting-alarm-deploy`를 지정하고 WSL에서 에이전트를 연결합니다. 에이전트는 Java 21로 실행하고 작업 경로는 `/home/사용자명/jenkins-agent`로 설정합니다.

### 3. 배포

```bash
bash infra/scripts/setup.sh
```
AWS 환경을 배포하여 리소스를 구성합니다.
`setup.sh` 실행 후에 출력된 DNS를 등록합니다.
인증서가 발급되면 `deploy-dev.sh`를 실행해 Jenkins에서 `main` 코드를 배포합니다.

```bash
bash infra/scripts/deploy-dev.sh
```

모니터링이 필요한 경우에는 다음 명령어를 실행합니다.

``` bash
bash infra/scripts/monitoring.sh
```
접속을 허용할 공인 IP를 `공인IP/32` 형식으로 입력합니다.
완료 후 출력되는 Grafana 전용 ALB 주소를 DNS의 `grafana` CNAME에 등록합니다.

### 4. 접속

최초 배포 후 `deployment-info.json`을 확인해 DNS CNAME과 Google, Kakao, Naver 리다이렉트 URL을 등록합니다. Grafana 접속 정보는 모니터링 설치 시에 출력됩니다.

이후 [https://app.chatting-alarm.p-e.kr](https://app.chatting-alarm.p-e.kr)에 접속합니다.


### 5. 리소스 삭제

```bash
bash infra/scripts/destroy.sh
```

실행 중인 Jenkins 배포를 중단한 뒤 프로젝트 루트에서 실행합니다. 애플리케이션과 모니터링 데이터는 삭제되며 state 저장용 S3 버킷은 유지됩니다.

## Trouble Shooting

### AWS Load Balancer Controller 시작 실패

| &nbsp;구&#8288;분&nbsp; | 내용 |
|---|---|
| &nbsp;문&#8288;제&nbsp; | ALB Controller 실행 시 `failed to get VPC ID ec2imds: GetMetadata context deadline exceeded` 오류가 발생했습니다. |
| &nbsp;원&#8288;인&nbsp; | EC2 Instance Metadata(IMDS) 요청이 시간 초과되어 Controller가 실행에 필요한 VPC ID를 가져오지 못했습니다. |
| &nbsp;해&#8288;결&nbsp; | `apply.sh`에서 Terraform의 `vpc_id` output을 읽고, `--aws-vpc-id=${VPC_ID}` 옵션으로 Controller에 직접 전달했습니다. |

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

### EBS CSI 드라이버 누락으로 모니터링 설치 중단

| &nbsp;구&#8288;분&nbsp; | 내용 |
|---|---|
| &nbsp;문&#8288;제&nbsp; | Prometheus·Grafana 설치 중 `ebs.csi.aws.com not found` 오류로 설치 스크립트가 중단됐습니다. |
| &nbsp;원&#8288;인&nbsp; | 모니터링 데이터를 EBS에 저장하도록 설정했지만, 볼륨 생성과 연결을 담당하는 EBS CSI 드라이버가 클러스터에 없었습니다. |
| &nbsp;해&#8288;결&nbsp; | Terraform에 EBS CSI 추가 기능과 전용 IAM Role을 추가하고 IRSA로 권한을 연결했습니다. 드라이버가 정상 실행되는 것을 확인한 뒤 모니터링 설치를 다시 진행했습니다. |

## 배운 점
- **Kubernetes 배포:** ALB와 Ingress를 활용한 요청 라우팅과 Pod 분산 배치 방법을 이해했습니다.
- **Redis Pub/Sub:** 서로 다른 Pod에 연결된 사용자 간 채팅 메세지를 공유하는 방법을 배웠습니다.
- **CI/CD 구성:** Jenkins로 테스트와 빌드 과정을 자동화하고, AWS 배포 파이프라인을 구성하는 경험을 했습니다.

## 아쉬운 점

- **성능 검증 부족:** 여러 사용자가 동시에 채팅하는 상황을 테스트하지 못했습니다. 이후에 부하 테스트를 진행하여 서버 자원 사용량이나 메세지 지연 시간 등을 확인하고 싶습니다.

- **운영 경험 부족:** 실제 운영 환경에서 장기간 서비스를 운영해보지 못한 점이 아쉽습니다. 운영 중 발생할 수 있는 장애 상황 등에 대한 경험을 하며 서비스의 안정성을 높이고 싶습니다.

## 개발자

- GitHub: [lty02-ops](https://github.com/lty02-ops)
