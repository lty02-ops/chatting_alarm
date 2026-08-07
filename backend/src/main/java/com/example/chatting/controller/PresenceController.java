package com.example.chatting.controller;

import com.example.chatting.entity.AppUser;
import com.example.chatting.service.CurrentUserService;
import com.example.chatting.service.PresenceService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/presence")
@RequiredArgsConstructor
public class PresenceController {
    private final CurrentUserService currentUserService;
    private final PresenceService presenceService;

    @PostMapping("/heartbeat")
    public ResponseEntity<Void> heartbeat(@AuthenticationPrincipal OAuth2User principal, HttpSession session) {
        AppUser user = currentUserService.require(principal, session);
        presenceService.heartbeat(user.getId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> offline(@AuthenticationPrincipal OAuth2User principal, HttpSession session) {
        AppUser user = currentUserService.require(principal, session);
        presenceService.offline(user.getId());
        return ResponseEntity.noContent().build();
    }
}
