package com.example.chatting;

import com.example.chatting.dto.ChatMessage;
import com.example.chatting.entity.ChatRoomMember;
import com.example.chatting.entity.Notification;
import com.example.chatting.repository.ChatRoomMemberRepository;
import com.example.chatting.repository.NotificationRepository;
import com.example.chatting.service.ChatService;
import com.example.chatting.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class NotificationFeatureTests {

    @Autowired
    private ChatService chatService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private ChatRoomMemberRepository chatRoomMemberRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void createsNotificationsForRoomMembersExceptSenderAndMarksOneAsRead() {
        saveMember("room-1", "alice");
        saveMember("room-1", "bob");
        saveMember("room-1", "charlie");

        ChatMessage message = new ChatMessage();
        message.setRoomId("room-1");
        message.setSender("alice");
        message.setContent("hello");
        message.setType(ChatMessage.MessageType.CHAT);

        chatService.saveMessage(message);

        assertThat(notificationRepository.findByRecipientOrderByCreatedAtDesc("alice")).isEmpty();
        assertThat(notificationRepository.findByRecipientOrderByCreatedAtDesc("bob"))
                .singleElement()
                .satisfies(notification -> {
                    assertThat(notification.getSender()).isEqualTo("alice");
                    assertThat(notification.getRoomId()).isEqualTo("room-1");
                    assertThat(notification.getMessageId()).isNotNull();
                    assertThat(notification.getContent()).isEqualTo("hello");
                    assertThat(notification.isRead()).isFalse();
                    assertThat(notification.getCreatedAt()).isNotNull();
                });
        assertThat(notificationRepository.findByRecipientOrderByCreatedAtDesc("charlie")).hasSize(1);

        List<Notification> notifications = notificationService.getNotifications("bob");
        Notification updated = notificationService.markAsRead(notifications.get(0).getId());

        assertThat(updated.isRead()).isTrue();
    }

    @Test
    void marksAllUnreadNotificationsInRoomAsRead() {
        saveMember("room-2", "alice");
        saveMember("room-2", "bob");

        ChatMessage first = chatMessage("room-2", "alice", "first");
        ChatMessage second = chatMessage("room-2", "alice", "second");
        chatService.saveMessage(first);
        chatService.saveMessage(second);

        int updatedCount = notificationService.markRoomAsRead("bob", "room-2");

        assertThat(updatedCount).isEqualTo(2);
        assertThat(notificationService.getNotifications("bob"))
                .allMatch(Notification::isRead);
    }

    private ChatMessage chatMessage(String roomId, String sender, String content) {
        ChatMessage message = new ChatMessage();
        message.setRoomId(roomId);
        message.setSender(sender);
        message.setContent(content);
        message.setType(ChatMessage.MessageType.CHAT);
        return message;
    }

    private void saveMember(String roomId, String nickname) {
        ChatRoomMember member = new ChatRoomMember();
        member.setRoomId(roomId);
        member.setUserNickname(nickname);
        member.setJoinedAt(LocalDateTime.now());
        chatRoomMemberRepository.save(member);
    }
}
