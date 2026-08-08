package com.example.chatting.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "friendship", uniqueConstraints =
        @UniqueConstraint(name = "uk_friend_pair", columnNames = {"user_a_id", "user_b_id"}))
@Getter @Setter @NoArgsConstructor
public class Friendship {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_a_id", nullable = false)
    private Long userAId;
    @Column(name = "user_b_id", nullable = false)
    private Long userBId;
    @Column(name = "requester_id")
    private Long requesterId;
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20) default 'ACCEPTED'")
    private String status = "PENDING";
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
