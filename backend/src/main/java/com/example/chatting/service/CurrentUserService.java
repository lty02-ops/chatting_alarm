package com.example.chatting.service;

import com.example.chatting.entity.AppUser;
import com.example.chatting.repository.AppUserRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
public class CurrentUserService {
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final AppUserRepository userRepository;
    private final SecureRandom random = new SecureRandom();

    public AppUser require(OAuth2User principal, HttpSession session) {
        Long id = principal != null
                ? Long.valueOf(String.valueOf(principal.getAttribute("internalUserId")))
                : (Long) session.getAttribute("userId");
        if (id == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return userRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    public String ensureFriendCode(AppUser user) {
        if (user.getFriendCode() != null && !user.getFriendCode().isBlank()) return user.getFriendCode();
        String code;
        do {
            StringBuilder value = new StringBuilder();
            for (int i = 0; i < 8; i++) value.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
            code = value.toString();
        } while (userRepository.existsByFriendCode(code));
        user.setFriendCode(code);
        userRepository.save(user);
        return code;
    }
}
