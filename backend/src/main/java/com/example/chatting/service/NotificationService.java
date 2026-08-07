package com.example.chatting.service;

import com.example.chatting.entity.ChatMessageEntity;
import com.example.chatting.entity.ChatRoomMember;
import com.example.chatting.entity.Notification;
import com.example.chatting.repository.ChatRoomMemberRepository;
import com.example.chatting.repository.NotificationRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;

    @Transactional
    public void createForChatMessage(ChatMessageEntity message) {
        List<Notification> notifications = chatRoomMemberRepository.findAllByRoomId(message.getRoomId()).stream()
                .map(ChatRoomMember::getUserNickname)
                .filter(recipient -> !recipient.equals(message.getSender()))
                .distinct()
                .map(recipient -> createNotification(recipient, message))
                .toList();

        notificationRepository.saveAll(notifications);
    }

    public List<Notification> getNotifications(String recipient) {
        return notificationRepository.findByRecipientOrderByCreatedAtDesc(recipient);
    }

    @Transactional
    public Notification markAsRead(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        notification.setRead(true);
        return notification;
    }

    @Transactional
    public int markRoomAsRead(String recipient, String roomId) {
        List<Notification> notifications = notificationRepository
                .findByRecipientAndRoomIdAndReadFalse(recipient, roomId);
        notifications.forEach(notification -> notification.setRead(true));
        return notifications.size();
    }

    @Transactional
    public void deleteForRoom(String recipient, String roomId) {
        notificationRepository.deleteByRecipientAndRoomId(recipient, roomId);
    }

    private Notification createNotification(String recipient, ChatMessageEntity message) {
        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setSender(message.getSender());
        notification.setRoomId(message.getRoomId());
        notification.setMessageId(message.getId());
        notification.setContent(message.getContent());
        return notification;
    }
}
