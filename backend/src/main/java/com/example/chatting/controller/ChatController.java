package com.example.chatting.controller;

import com.example.chatting.dto.ChatMessage;
import com.example.chatting.service.ChatService;
import com.example.chatting.service.ChatSessionManager;
import com.example.chatting.service.RealtimePublisher;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;

@Controller
public class ChatController {

    // SimpMessagingTemplate: 특정 브로커 주소("/topic/...")로 메시지를 보낼 때 사용 (강력한 메시지 전송 도구)
    private final RealtimePublisher realtimePublisher;
    // ChatService: 메시지 저장, 중복 확인 등 DB와 관련된 비즈니스 로직 처리
    private final ChatService chatService;
    // ChatSessionManager: 현재 접속 중인 사용자들의 세션 상태를 관리 (누가 접속 중인지 관리)
    private final ChatSessionManager sessionManager;

    public ChatController(RealtimePublisher realtimePublisher,
                          ChatService chatService,
                          ChatSessionManager sessionManager) {
        this.realtimePublisher = realtimePublisher;
        this.chatService = chatService;
        this.sessionManager = sessionManager;
    }


    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload ChatMessage chatMessage, SimpMessageHeaderAccessor headerAccessor) {
        String roomId = chatMessage.getRoomId(); // 채팅방 ID 추출
        String sender = chatMessage.getSender(); // 보낸 사람 이름 추출

        // --- 1. 입장(JOIN) 처리 로직 ---
        if (chatMessage.getType() == ChatMessage.MessageType.JOIN) {
            // [비즈니스 로직] 처음 들어온 멤버라면 DB에 등록 (입장 시간 기록 등)
            // REST 입장 확인을 우회한 WebSocket 요청도 서버에서 정원을 다시 검사합니다.
            if (!chatService.joinRoom(roomId, sender)) {
                return;
            }

            // [세션 관리] 현재 실시간 접속자 목록에 추가
            sessionManager.addMember(roomId, sender);

            // [세션 저장] 웹소켓 연결 세션 내부에 방 정보와 이름을 저장 (연결이 끊겼을 때 누구였는지 알기 위함)
            if (headerAccessor.getSessionAttributes() != null) {
                headerAccessor.getSessionAttributes().put("roomId", roomId);
                headerAccessor.getSessionAttributes().put("username", sender);
            }

            // /topic/room/{roomId}/online-status 주소를 구독 중인 프론트엔드에게 명단을 쏩니다.
            realtimePublisher.publish("/topic/room/" + roomId + "/online-status", sessionManager.getMemberList(roomId));

            // JOIN은 접속자 상태 갱신에만 사용하며 채팅 메시지로 저장하거나 표시하지 않습니다.
            return; // 입장 처리가 끝났으므로 메서드 종료
        }

        // --- 2. 퇴장(LEAVE) 처리 로직 (사용자가 명시적으로 나갈 때) ---
        if (chatMessage.getType() == ChatMessage.MessageType.LEAVE) {
            // [세션 관리] 실시간 접속자 목록에서 삭제
            sessionManager.removeMember(roomId, sender);

            // 시스템 메시지 시간 설정
            chatMessage.setTimestamp(LocalDateTime.now());

            // 퇴장 사실을 방 사람들에게 실시간 전송 (필요 시 DB 저장 로직 추가 가능)
            realtimePublisher.publish("/topic/room/" + roomId, chatMessage);
            return;
        }

        // --- 3. 일반 채팅 메시지(CHAT) 처리 로직 ---
        chatMessage.setTimestamp(LocalDateTime.now()); // 서버 도착 시간 설정
        var savedMessage = chatService.saveMessage(chatMessage); // DB에 채팅 내용 저장
        chatMessage.setId(savedMessage.getId());
        chatMessage.setTimestamp(savedMessage.getTimestamp());

        // 해당 방(roomId)의 구독자들에게 메시지 전달 (실시간 채팅의 본질)
        realtimePublisher.publish("/topic/room/" + roomId, chatMessage);
    }
}
