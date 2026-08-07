package com.example.chatting.repository;

import com.example.chatting.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientOrderByCreatedAtDesc(String recipient);

    List<Notification> findByRecipientAndRoomIdAndReadFalse(String recipient, String roomId);

    void deleteByRecipientAndRoomId(String recipient, String roomId);

    List<Notification> findAllByRecipient(String recipient);

    List<Notification> findAllBySender(String sender);
}
