package com.example.chatting.controller;

import com.example.chatting.entity.AppUser;
import com.example.chatting.entity.MessageReaction;
import com.example.chatting.repository.ChatMessageRepository;
import com.example.chatting.repository.MessageReactionRepository;
import com.example.chatting.service.ChatService;
import com.example.chatting.service.CurrentUserService;
import com.example.chatting.service.ObjectStorageService;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

@RestController
@RequiredArgsConstructor
public class ChatMediaController {
    private static final Set<String> ALLOWED_EMOJIS = Set.of("❤️", "👍", "😂", "😮", "😢", "🎉");
    private final CurrentUserService currentUserService;
    private final ChatService chatService;
    private final ChatMessageRepository messageRepository;
    private final MessageReactionRepository reactionRepository;
    private final ObjectStorageService objectStorageService;

    @Value("${app.attachment-upload-dir:/data/chat-attachments}")
    private String uploadDirectory;

    @PostMapping(value = "/chat/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file,
                                    @AuthenticationPrincipal OAuth2User principal,
                                    HttpSession session) throws Exception {
        currentUserService.require(principal, session);
        if (file.isEmpty() || file.getSize() > 20 * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of("message", "파일은 20MB까지 첨부할 수 있습니다."));
        }
        String original = Optional.ofNullable(file.getOriginalFilename()).orElse("file");
        String safeExtension = original.lastIndexOf('.') >= 0 ? original.substring(original.lastIndexOf('.')).replaceAll("[^A-Za-z0-9.]", "") : "";
        String filename = UUID.randomUUID() + safeExtension;
        if (objectStorageService.enabled()) {
            objectStorageService.put("attachments/" + filename, file);
        } else {
            Path directory = Path.of(uploadDirectory).toAbsolutePath().normalize();
            Files.createDirectories(directory);
            Files.copy(file.getInputStream(), directory.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
        }
        return ResponseEntity.ok(Map.of("url", "/attachments/" + filename, "name", original,
                "type", Optional.ofNullable(file.getContentType()).orElse("application/octet-stream")));
    }

    @GetMapping("/attachments/{filename:.+}")
    public ResponseEntity<?> attachment(@PathVariable String filename) throws Exception {
        if (objectStorageService.enabled()) {
            var stored = objectStorageService.get("attachments/" + filename);
            if (stored == null) return ResponseEntity.notFound().build();
            String type = stored.contentType() == null ? "application/octet-stream" : stored.contentType();
            return ResponseEntity.ok().contentType(MediaType.parseMediaType(type)).body(stored.content());
        }
        Path directory = Path.of(uploadDirectory).toAbsolutePath().normalize();
        Path file = directory.resolve(filename).normalize();
        if (!file.startsWith(directory) || !Files.isRegularFile(file)) return ResponseEntity.notFound().build();
        String type = Files.probeContentType(file);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(type == null ? "application/octet-stream" : type))
                .body(new UrlResource(file.toUri()));
    }

    @GetMapping("/chat/room/{roomId}/reactions")
    public List<Map<String, Object>> reactions(@PathVariable String roomId,
                                               @AuthenticationPrincipal OAuth2User principal,
                                               HttpSession session) {
        AppUser me = currentUserService.require(principal, session);
        List<Long> ids = messageRepository.findAllByRoomId(roomId).stream().map(message -> message.getId()).toList();
        if (ids.isEmpty()) return List.of();
        Map<Long, List<MessageReaction>> grouped = new LinkedHashMap<>();
        reactionRepository.findAllByMessageIdIn(ids).forEach(reaction ->
                grouped.computeIfAbsent(reaction.getMessageId(), ignored -> new ArrayList<>()).add(reaction));
        return grouped.entrySet().stream().map(entry -> reactionView(entry.getKey(), entry.getValue(), me.getNickname())).toList();
    }

    @PostMapping("/chat/messages/{messageId}/reactions")
    @Transactional
    public ResponseEntity<?> toggleReaction(@PathVariable Long messageId, @RequestParam String emoji,
                                            @AuthenticationPrincipal OAuth2User principal, HttpSession session) {
        AppUser me = currentUserService.require(principal, session);
        var message = messageRepository.findById(messageId).orElse(null);
        if (message == null || !chatService.isMember(message.getRoomId(), me.getNickname())) return ResponseEntity.notFound().build();
        if (!ALLOWED_EMOJIS.contains(emoji)) return ResponseEntity.badRequest().build();
        reactionRepository.findByMessageIdAndUserNicknameAndEmoji(messageId, me.getNickname(), emoji)
                .ifPresentOrElse(reactionRepository::delete, () -> {
                    MessageReaction reaction = new MessageReaction();
                    reaction.setMessageId(messageId); reaction.setUserNickname(me.getNickname()); reaction.setEmoji(emoji);
                    reactionRepository.save(reaction);
                });
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> reactionView(Long messageId, List<MessageReaction> reactions, String me) {
        Map<String, Long> counts = new LinkedHashMap<>();
        Set<String> mine = new HashSet<>();
        reactions.forEach(reaction -> {
            counts.merge(reaction.getEmoji(), 1L, Long::sum);
            if (reaction.getUserNickname().equals(me)) mine.add(reaction.getEmoji());
        });
        return Map.of("messageId", messageId, "counts", counts, "mine", mine);
    }
}
