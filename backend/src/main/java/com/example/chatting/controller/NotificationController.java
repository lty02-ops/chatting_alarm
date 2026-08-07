package com.example.chatting.controller;

import com.example.chatting.entity.Notification;
import com.example.chatting.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public List<Notification> getNotifications(@RequestParam String recipient) {
        return notificationService.getNotifications(recipient);
    }

    @PatchMapping("/{id}/read")
    public Notification markAsRead(@PathVariable Long id) {
        return notificationService.markAsRead(id);
    }

    @PatchMapping("/rooms/{roomId}/read")
    public Map<String, Integer> markRoomAsRead(
            @PathVariable String roomId,
            @RequestParam String recipient) {
        int updatedCount = notificationService.markRoomAsRead(recipient, roomId);
        return Map.of("updatedCount", updatedCount);
    }
}
