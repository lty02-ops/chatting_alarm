package com.example.chatting.listener;

import com.example.chatting.service.ChatSessionManager;
import com.example.chatting.service.RealtimePublisher;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.List;


@Component
public class WebSocketEventListener {
    // 실시간 접속자 명단을 관리하는 매니저 객체
    private final ChatSessionManager sessionManager;
    private final RealtimePublisher realtimePublisher;

    public WebSocketEventListener(ChatSessionManager sessionManager, RealtimePublisher realtimePublisher) {
        this.sessionManager = sessionManager;
        this.realtimePublisher = realtimePublisher;
    }


    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String username = (String) headerAccessor.getSessionAttributes().get("username");
        String roomId = (String) headerAccessor.getSessionAttributes().get("roomId");

        if (username != null && roomId != null) {
            // 1. 실시간 명단에서 제거 (청소)
            sessionManager.removeMember(roomId, username);

            // 2. [수정] 최신 명단을 가져와서 프론트엔드가 구독 중인 주소로 발송
            // 프론트엔드 HTML의 'stompClient.subscribe' 주소와 동일하게 '/online-status'로 변경합니다.
            List<String> onlineMembers = sessionManager.getMemberList(roomId);
            realtimePublisher.publish("/topic/room/" + roomId + "/online-status", onlineMembers);

        }
    }
}
