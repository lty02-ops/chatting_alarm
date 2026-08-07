package com.example.chatting.controller;

import com.example.chatting.entity.AppUser;
import com.example.chatting.repository.AppUserRepository;
import com.example.chatting.repository.ChatMessageRepository;
import com.example.chatting.repository.ChatRoomMemberRepository;
import com.example.chatting.repository.NotificationRepository;
import com.example.chatting.service.CurrentUserService;
import com.example.chatting.service.ObjectStorageService;
import jakarta.servlet.http.HttpSession;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import jakarta.transaction.Transactional;

@RestController
@RequiredArgsConstructor
public class ProfileController {
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    private final AppUserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final ChatRoomMemberRepository memberRepository;
    private final ChatMessageRepository messageRepository;
    private final NotificationRepository notificationRepository;
    private final ObjectStorageService objectStorageService;

    @Value("${app.profile-upload-dir:/data/profile-images}")
    private String uploadDirectory;

    @PostMapping(value = "/auth/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(@RequestParam("image") MultipartFile image,
                                    @AuthenticationPrincipal OAuth2User principal,
                                    HttpSession session) throws Exception {
        AppUser user = currentUserService.require(principal, session);
        if (image.isEmpty() || image.getSize() > 5 * 1024 * 1024 || !ALLOWED_TYPES.contains(image.getContentType())) {
            return ResponseEntity.badRequest().body(Map.of("message", "JPG, PNG, WEBP, GIF 이미지만 5MB까지 업로드할 수 있습니다."));
        }

        String extension = switch (image.getContentType()) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> ".jpg";
        };
        String filename = user.getId() + "-" + UUID.randomUUID() + extension;
        Path directory = Path.of(uploadDirectory).toAbsolutePath().normalize();
        if (objectStorageService.enabled()) {
            objectStorageService.put("profiles/" + filename, image);
        } else {
            Files.createDirectories(directory);
            Files.copy(image.getInputStream(), directory.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
        }

        String oldUrl = user.getProfileImageUrl();
        user.setProfileImageUrl("/profiles/" + filename);
        userRepository.save(user);
        deleteOldImage(directory, oldUrl);
        return ResponseEntity.ok(Map.of("profileImageUrl", user.getProfileImageUrl()));
    }

    @PatchMapping("/auth/profile")
    @Transactional
    public ResponseEntity<?> updateName(@RequestBody Map<String, String> request,
                                        @AuthenticationPrincipal OAuth2User principal,
                                        HttpSession session) {
        AppUser user = currentUserService.require(principal, session);
        String nickname = request.getOrDefault("nickname", "").trim();
        if (nickname.isBlank() || nickname.length() > 40) return ResponseEntity.badRequest().build();
        if (nickname.equals(user.getNickname())) return ResponseEntity.ok(profileResponse(user));
        if (userRepository.existsByNickname(nickname)) return ResponseEntity.status(409).build();

        String oldNickname = user.getNickname();
        memberRepository.findAllByUserNickname(oldNickname).forEach(member -> member.setUserNickname(nickname));
        messageRepository.findAllBySender(oldNickname).forEach(message -> message.setSender(nickname));
        notificationRepository.findAllByRecipient(oldNickname).forEach(notification -> notification.setRecipient(nickname));
        notificationRepository.findAllBySender(oldNickname).forEach(notification -> notification.setSender(nickname));
        user.setNickname(nickname);
        userRepository.save(user);
        return ResponseEntity.ok(profileResponse(user));
    }

    @GetMapping("/profiles/{filename:.+}")
    public ResponseEntity<?> image(@PathVariable String filename) throws Exception {
        if (objectStorageService.enabled()) {
            var stored = objectStorageService.get("profiles/" + filename);
            if (stored == null) return ResponseEntity.notFound().build();
            String contentType = stored.contentType() == null ? "application/octet-stream" : stored.contentType();
            return ResponseEntity.ok().contentType(MediaType.parseMediaType(contentType)).body(stored.content());
        }
        Path directory = Path.of(uploadDirectory).toAbsolutePath().normalize();
        Path file = directory.resolve(filename).normalize();
        if (!file.startsWith(directory) || !Files.isRegularFile(file)) return ResponseEntity.notFound().build();
        String contentType = Files.probeContentType(file);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType == null ? "application/octet-stream" : contentType))
                .body(new UrlResource(file.toUri()));
    }

    private void deleteOldImage(Path directory, String oldUrl) {
        if (oldUrl == null || !oldUrl.startsWith("/profiles/")) return;
        try {
            String filename = oldUrl.substring("/profiles/".length());
            if (objectStorageService.enabled()) {
                objectStorageService.delete("profiles/" + filename);
            } else {
                Path oldFile = directory.resolve(filename).normalize();
                if (oldFile.startsWith(directory)) Files.deleteIfExists(oldFile);
            }
        } catch (Exception ignored) {
        }
    }

    private Map<String, Object> profileResponse(AppUser user) {
        return Map.of("id", user.getId(), "nickname", user.getNickname(), "provider", user.getProvider(),
                "friendCode", currentUserService.ensureFriendCode(user),
                "profileImageUrl", user.getProfileImageUrl() == null ? "" : user.getProfileImageUrl());
    }
}
