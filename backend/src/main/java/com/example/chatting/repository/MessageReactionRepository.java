package com.example.chatting.repository;

import com.example.chatting.entity.MessageReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface MessageReactionRepository extends JpaRepository<MessageReaction, Long> {
    List<MessageReaction> findAllByMessageIdIn(List<Long> messageIds);
    Optional<MessageReaction> findByMessageIdAndUserNicknameAndEmoji(Long messageId, String userNickname, String emoji);
}
