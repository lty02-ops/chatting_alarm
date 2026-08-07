package com.example.chatting.controller;

import com.example.chatting.entity.AppUser;
import com.example.chatting.entity.Friendship;
import com.example.chatting.repository.AppUserRepository;
import com.example.chatting.repository.FriendshipRepository;
import com.example.chatting.service.CurrentUserService;
import com.example.chatting.service.PresenceService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/friends")
@RequiredArgsConstructor
public class FriendController {
    private final CurrentUserService currentUserService;
    private final AppUserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final PresenceService presenceService;

    public record FriendView(Long id, String nickname, String friendCode, String profileImageUrl, boolean online) {}

    @GetMapping
    public List<FriendView> friends(@AuthenticationPrincipal OAuth2User principal, HttpSession session) {
        AppUser me = currentUserService.require(principal, session);
        return friendshipRepository.findByUserAIdOrUserBId(me.getId(), me.getId()).stream()
                .map(friendship -> friendship.getUserAId().equals(me.getId()) ? friendship.getUserBId() : friendship.getUserAId())
                .map(userRepository::findById).flatMap(java.util.Optional::stream)
                .map(this::view).toList();
    }

    @PostMapping("/code")
    public ResponseEntity<?> addByCode(@RequestParam String code,
                                       @AuthenticationPrincipal OAuth2User principal,
                                       HttpSession session) {
        AppUser me = currentUserService.require(principal, session);
        AppUser friend = userRepository.findByFriendCodeIgnoreCase(code.trim())
                .orElse(null);
        if (friend == null) return ResponseEntity.notFound().build();
        if (friend.getId().equals(me.getId())) {
            return ResponseEntity.badRequest().body(Map.of("message", "자기 자신은 친구로 추가할 수 없습니다."));
        }
        long a = Math.min(me.getId(), friend.getId());
        long b = Math.max(me.getId(), friend.getId());
        friendshipRepository.findByUserAIdAndUserBId(a, b).orElseGet(() -> {
            Friendship friendship = new Friendship();
            friendship.setUserAId(a);
            friendship.setUserBId(b);
            return friendshipRepository.save(friendship);
        });
        return ResponseEntity.status(HttpStatus.CREATED).body(view(friend));
    }

    @DeleteMapping("/{friendId}")
    public ResponseEntity<?> deleteFriend(@PathVariable Long friendId,
                                          @AuthenticationPrincipal OAuth2User principal,
                                          HttpSession session) {
        AppUser me = currentUserService.require(principal, session);
        long a = Math.min(me.getId(), friendId);
        long b = Math.max(me.getId(), friendId);
        return friendshipRepository.findByUserAIdAndUserBId(a, b)
                .map(friendship -> {
                    friendshipRepository.delete(friendship);
                    return ResponseEntity.noContent().build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private FriendView view(AppUser user) {
        return new FriendView(user.getId(), user.getNickname(), user.getFriendCode(),
                user.getProfileImageUrl() == null ? "" : user.getProfileImageUrl(),
                presenceService.isOnline(user.getId()));
    }
}
