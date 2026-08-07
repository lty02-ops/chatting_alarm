package com.example.chatting.repository;

import com.example.chatting.entity.ChatMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;


public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {


    List<ChatMessageEntity> findByRoomIdAndTimestampAfterOrderByTimestampAsc(String roomId, LocalDateTime timestamp);


    List<ChatMessageEntity> findAllBySender(String sender);

    List<ChatMessageEntity> findAllByRoomId(String roomId);
}
