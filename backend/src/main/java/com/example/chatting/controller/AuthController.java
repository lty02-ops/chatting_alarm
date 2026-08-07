package com.example.chatting.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import com.example.chatting.entity.AppUser;
import com.example.chatting.repository.AppUserRepository;
import com.example.chatting.service.CurrentUserService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AppUserRepository userRepository;
    private final CurrentUserService currentUserService;

    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal OAuth2User principal, HttpSession session) {
        AppUser user;
        try { user = currentUserService.require(principal, session); }
        catch (Exception exception) { return ResponseEntity.status(401).build(); }
        return ResponseEntity.ok(userResponse(user));
    }

    @PostMapping("/dev-login")
    public ResponseEntity<?> devLogin(@RequestParam String nickname, HttpSession session) {
        String clean = nickname.trim();
        if (clean.isBlank()) return ResponseEntity.badRequest().build();
        AppUser user = userRepository.findByProviderAndProviderId("DEV", clean.toLowerCase()).orElseGet(() -> {
            if (userRepository.existsByNickname(clean)) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다.");
            }
            AppUser created = new AppUser();
            created.setProvider("DEV");
            created.setProviderId(clean.toLowerCase());
            created.setNickname(clean);
            return userRepository.save(created);
        });
        currentUserService.ensureFriendCode(user);
        session.setAttribute("userId", user.getId());
        return ResponseEntity.ok(userResponse(user));
    }

    private Map<String, Object> userResponse(AppUser user) {
        return Map.of("id", user.getId(), "nickname", user.getNickname(), "provider", user.getProvider(),
                "friendCode", currentUserService.ensureFriendCode(user),
                "profileImageUrl", user.getProfileImageUrl() == null ? "" : user.getProfileImageUrl());
    }
}
