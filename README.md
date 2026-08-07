# Chatting Alarm

- `frontend`: React + Vite, STOMP/SockJS 클라이언트
- `backend`: Spring Boot, WebSocket/STOMP, JPA, MySQL

## 로컬 실행

```powershell
cd backend
.\gradlew.bat bootRun
```

```powershell
cd frontend
npm install
npm run dev
```

`http://localhost:5173`으로 접속합니다. 배포 환경에서는 `VITE_API_BASE_URL=https://api.example.com`을 설정합니다.

## 알림 API

일반 채팅 메시지가 저장되면 같은 채팅방의 발신자를 제외한 참여자별 알림이 생성됩니다.

```text
GET   /notifications?recipient={nickname}
PATCH /notifications/{id}/read
```

현재 단계에서는 알림을 MySQL에 저장하고 조회·읽음 처리하며 Kafka와 FCM 전송은 포함하지 않습니다.
