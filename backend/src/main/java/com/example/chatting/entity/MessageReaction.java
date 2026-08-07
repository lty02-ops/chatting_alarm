package com.example.chatting.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "message_reaction", uniqueConstraints =
        @UniqueConstraint(name = "uk_message_user_emoji", columnNames = {"message_id", "user_nickname", "emoji"}))
@Getter @Setter
public class MessageReaction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "message_id", nullable = false)
    private Long messageId;
    @Column(name = "user_nickname", nullable = false, length = 40)
    private String userNickname;
    @Column(nullable = false, length = 16)
    private String emoji;
}
