package com.example.chatting.repository;

import com.example.chatting.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByProviderAndProviderId(String provider, String providerId);
    Optional<AppUser> findByNickname(String nickname);
    Optional<AppUser> findByFriendCodeIgnoreCase(String friendCode);
    boolean existsByNickname(String nickname);
    boolean existsByFriendCode(String friendCode);
}
