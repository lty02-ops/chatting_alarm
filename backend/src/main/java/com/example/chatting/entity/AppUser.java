package com.example.chatting.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "app_user", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_provider", columnNames = {"provider", "provider_id"}),
        @UniqueConstraint(name = "uk_user_nickname", columnNames = "nickname"),
        @UniqueConstraint(name = "uk_user_friend_code", columnNames = "friend_code")
})
@Getter
@Setter
@NoArgsConstructor
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(name = "provider_id", nullable = false, length = 191)
    private String providerId;

    private String email;

    @Column(nullable = false, length = 40)
    private String nickname;

    @Column(name = "friend_code", length = 12)
    private String friendCode;

    @Column(name = "profile_image_url", length = 1000)
    private String profileImageUrl;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
