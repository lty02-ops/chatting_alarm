package com.example.chatting.controller;

import com.example.chatting.entity.ChatRoom;
import com.example.chatting.entity.ChatMessageEntity;
import com.example.chatting.entity.AppUser;
import com.example.chatting.dto.ChatMessage;
import com.example.chatting.repository.AppUserRepository;
import com.example.chatting.repository.FriendshipRepository;
import com.example.chatting.repository.ChatRoomRepository;
import com.example.chatting.repository.ChatRoomMemberRepository;
import com.example.chatting.service.ChatService;
import com.example.chatting.service.ChatSessionManager;
import com.example.chatting.service.NotificationService;
import com.example.chatting.service.CurrentUserService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor // final이 붙은 필드들을 모아 생성자를 자동으로 생성 (Lombok)
public class ChatRoomController {

    public record RoomSummary(String roomId, String name, long currentParticipants, int maxParticipants, String roomType, String profileImageUrl) {}
    public record GroupRoomRequest(String name, List<Long> friendIds) {}

    private final ChatRoomRepository chatRoomRepository;
    private final ChatService chatService;
    private final ChatSessionManager sessionManager;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatRoomMemberRepository memberRepository;
    private final AppUserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final CurrentUserService currentUserService;


    @GetMapping("/rooms")
    public List<RoomSummary> rooms(@RequestParam("nickname") String nickname) {
        return memberRepository.findAllByUserNickname(nickname).stream()
                .map(member -> chatRoomRepository.findById(member.getRoomId()))
                .flatMap(java.util.Optional::stream)
                .map(room -> summary(room, nickname))
                .toList();
    }

    @PostMapping("/direct/{friendId}")
    public ResponseEntity<?> directRoom(@PathVariable Long friendId,
                                        @AuthenticationPrincipal OAuth2User principal,
                                        HttpSession session) {
        AppUser me = currentUserService.require(principal, session);
        AppUser friend = userRepository.findById(friendId).orElse(null);
        if (friend == null || !areFriends(me.getId(), friendId)) return ResponseEntity.status(403).build();
        String key = Math.min(me.getId(), friendId) + ":" + Math.max(me.getId(), friendId);
        ChatRoom room = chatRoomRepository.findByDirectKey(key).orElseGet(() -> {
            ChatRoom created = chatRoomRepository.save(ChatRoom.direct(friend.getNickname(), key, me.getId()));
            return created;
        });
        chatService.registerMemberIfFirstTime(room.getRoomId(), me.getNickname());
        chatService.registerMemberIfFirstTime(room.getRoomId(), friend.getNickname());
        return ResponseEntity.ok(summary(room, me.getNickname()));
    }

    @PostMapping("/group")
    public ResponseEntity<?> groupRoom(@RequestBody GroupRoomRequest request,
                                       @AuthenticationPrincipal OAuth2User principal,
                                       HttpSession session) {
        AppUser me = currentUserService.require(principal, session);
        List<Long> ids = request.friendIds() == null ? List.of() : request.friendIds().stream().distinct().toList();
        if (request.name() == null || request.name().isBlank() || ids.isEmpty()) return ResponseEntity.badRequest().build();
        if (ids.stream().anyMatch(id -> !areFriends(me.getId(), id))) return ResponseEntity.status(403).build();
        List<AppUser> invited = userRepository.findAllById(ids);
        if (invited.size() != ids.size()) return ResponseEntity.badRequest().build();
        ChatRoom room = chatRoomRepository.save(ChatRoom.group(request.name().trim(), invited.size() + 1, me.getId()));
        chatService.registerMemberIfFirstTime(room.getRoomId(), me.getNickname());
        invited.forEach(friend -> chatService.registerMemberIfFirstTime(room.getRoomId(), friend.getNickname()));

        ChatMessage inviteMessage = new ChatMessage();
        inviteMessage.setRoomId(room.getRoomId());
        inviteMessage.setSender(me.getNickname());
        inviteMessage.setType(ChatMessage.MessageType.INVITE);
        inviteMessage.setContent(me.getNickname() + "님이 초대하셨습니다.");
        inviteMessage.setTimestamp(LocalDateTime.now());
        chatService.saveMessage(inviteMessage);
        return ResponseEntity.status(201).body(summary(room, me.getNickname()));
    }


    @PostMapping("/room/{roomId}/members")
    public ResponseEntity<?> joinRoom(
            @PathVariable String roomId,
            @RequestParam("nickname") String nickname) {
        if (!chatRoomRepository.existsById(roomId)) {
            return ResponseEntity.notFound().build();
        }
        if (!chatService.joinRoom(roomId, nickname)) {
            return ResponseEntity.status(409).body(Map.of("message", "Chat room is full"));
        }
        return ResponseEntity.ok(Map.of("memberCount", chatService.getMemberCount(roomId)));
    }


    @GetMapping("/room/{roomId}/messages")
    public List<ChatMessageEntity> getRoomMessages(
            @PathVariable String roomId,
            @RequestParam("nickname") String nickname) {
        // 사용자가 처음 들어온 시간 이후의 메시지만 필터링하여 반환 (이전 대화 보기 방지)
        return chatService.getMessagesForUser(roomId, nickname);
    }


    @GetMapping("/room/{roomId}/members")
    public List<String> getRoomMembers(@PathVariable String roomId) {
        // 현재 해당 방에 참여 중인 유저들의 닉네임 리스트를 반환
        return chatService.getMemberList(roomId);
    }


    @GetMapping("/room/{roomId}/online-members")
    public List<String> getOnlineMembers(@PathVariable String roomId) {
        // ChatSessionManager는 현재 메모리에 올라와 있는(연결된) 유저만 알고 있습니다.
        return sessionManager.getMemberList(roomId);
    }

    @DeleteMapping("/room/{roomId}/members/{nickname}")
    public ResponseEntity<?> leaveRoomPermanently(
            @PathVariable String roomId,
            @PathVariable String nickname) {
        if (!chatService.isMember(roomId, nickname)) {
            return ResponseEntity.notFound().build();
        }

        ChatMessage leaveMessage = new ChatMessage();
        leaveMessage.setRoomId(roomId);
        leaveMessage.setSender(nickname);
        leaveMessage.setType(ChatMessage.MessageType.LEAVE);
        leaveMessage.setContent(nickname + "님이 채팅방을 나갔습니다.");
        leaveMessage.setTimestamp(LocalDateTime.now());
        chatService.saveMessage(leaveMessage);

        chatService.removeMember(roomId, nickname);
        notificationService.deleteForRoom(nickname, roomId);
        sessionManager.removeMember(roomId, nickname);

        List<String> remainingMembers = chatService.getMemberList(roomId);
        messagingTemplate.convertAndSend("/topic/room/" + roomId, leaveMessage);
        messagingTemplate.convertAndSend(
                "/topic/room/" + roomId + "/online-status",
                sessionManager.getMemberList(roomId));

        if (remainingMembers.isEmpty() && chatRoomRepository.existsById(roomId)) {
            chatRoomRepository.deleteById(roomId);
        }

        return ResponseEntity.ok(Map.of("remainingMemberCount", remainingMembers.size()));
    }

    private boolean areFriends(Long first, Long second) {
        long a = Math.min(first, second);
        long b = Math.max(first, second);
        return friendshipRepository.findByUserAIdAndUserBId(a, b).isPresent();
    }

    private RoomSummary summary(ChatRoom room, String viewerNickname) {
        String displayName = room.getName();
        String profileImageUrl = "";
        if ("DIRECT".equals(room.getRoomType()) && viewerNickname != null) {
            displayName = chatService.getMemberList(room.getRoomId()).stream()
                    .filter(member -> !member.equals(viewerNickname)).findFirst().orElse(room.getName());
            profileImageUrl = userRepository.findByNickname(displayName)
                    .map(AppUser::getProfileImageUrl).orElse("");
        }
        return new RoomSummary(room.getRoomId(), displayName, chatService.getMemberCount(room.getRoomId()),
                room.getMaxParticipants() > 0 ? room.getMaxParticipants() : 10,
                room.getRoomType() == null ? "GROUP" : room.getRoomType(), profileImageUrl);
    }
}
