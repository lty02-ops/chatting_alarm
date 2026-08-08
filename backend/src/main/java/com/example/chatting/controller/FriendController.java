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
    public record FriendRequestView(Long requestId, Long requesterId, String nickname, String profileImageUrl) {}

    @GetMapping
    public List<FriendView> friends(@AuthenticationPrincipal OAuth2User principal, HttpSession session) {
        AppUser me = currentUserService.require(principal, session);
        return friendshipRepository.findAcceptedByUserId(me.getId()).stream()
                .map(friendship -> friendship.getUserAId().equals(me.getId()) ? friendship.getUserBId() : friendship.getUserAId())
                .map(userRepository::findById).flatMap(java.util.Optional::stream)
                .map(this::view).toList();
    }

    @GetMapping("/requests")
    public List<FriendRequestView> incomingRequests(@AuthenticationPrincipal OAuth2User principal,
                                                     HttpSession session) {
        AppUser me = currentUserService.require(principal, session);
        return friendshipRepository.findIncomingRequests(me.getId()).stream()
                .map(friendship -> userRepository.findById(friendship.getRequesterId())
                        .map(user -> new FriendRequestView(
                                friendship.getId(),
                                user.getId(),
                                user.getNickname(),
                                user.getProfileImageUrl() == null ? "" : user.getProfileImageUrl()
                        )))
                .flatMap(java.util.Optional::stream)
                .toList();
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
        var existing = friendshipRepository.findByUserAIdAndUserBId(a, b);
        if (existing.isPresent()) {
            String message = "ACCEPTED".equals(existing.get().getStatus())
                    ? "이미 친구로 등록된 사용자입니다."
                    : "이미 처리 대기 중인 친구 요청이 있습니다.";
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", message));
        }
        Friendship friendship = new Friendship();
        friendship.setUserAId(a);
        friendship.setUserBId(b);
        friendship.setRequesterId(me.getId());
        friendship.setStatus("PENDING");
        friendshipRepository.save(friendship);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "친구 요청을 보냈습니다."));
    }

    @PostMapping("/requests/{requestId}/accept")
    public ResponseEntity<?> acceptRequest(@PathVariable Long requestId,
                                            @AuthenticationPrincipal OAuth2User principal,
                                            HttpSession session) {
        AppUser me = currentUserService.require(principal, session);
        Friendship friendship = friendshipRepository.findById(requestId).orElse(null);
        if (!isIncomingPendingRequest(friendship, me.getId())) return ResponseEntity.notFound().build();
        friendship.setStatus("ACCEPTED");
        friendshipRepository.save(friendship);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/requests/{requestId}")
    public ResponseEntity<?> rejectRequest(@PathVariable Long requestId,
                                            @AuthenticationPrincipal OAuth2User principal,
                                            HttpSession session) {
        AppUser me = currentUserService.require(principal, session);
        Friendship friendship = friendshipRepository.findById(requestId).orElse(null);
        if (!isIncomingPendingRequest(friendship, me.getId())) return ResponseEntity.notFound().build();
        friendshipRepository.delete(friendship);
        return ResponseEntity.noContent().build();
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

    private boolean isIncomingPendingRequest(Friendship friendship, Long userId) {
        return friendship != null
                && "PENDING".equals(friendship.getStatus())
                && !userId.equals(friendship.getRequesterId())
                && (userId.equals(friendship.getUserAId()) || userId.equals(friendship.getUserBId()));
    }
}
