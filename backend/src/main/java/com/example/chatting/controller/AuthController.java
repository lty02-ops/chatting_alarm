package com.example.chatting.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import com.example.chatting.entity.AppUser;
import com.example.chatting.service.CurrentUserService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final CurrentUserService currentUserService;

    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal OAuth2User principal, HttpSession session) {
        AppUser user;
        try { user = currentUserService.require(principal, session); }
        catch (Exception exception) { return ResponseEntity.status(401).build(); }
        return ResponseEntity.ok(userResponse(user));
    }

    private Map<String, Object> userResponse(AppUser user) {
        return Map.of("id", user.getId(), "nickname", user.getNickname(), "provider", user.getProvider(),
                "friendCode", currentUserService.ensureFriendCode(user),
                "profileImageUrl", user.getProfileImageUrl() == null ? "" : user.getProfileImageUrl());
    }
}
