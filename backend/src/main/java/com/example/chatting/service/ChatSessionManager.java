package com.example.chatting.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;


@Service
@RequiredArgsConstructor
public class ChatSessionManager {


    private final Map<String, Set<String>> roomUsers = new ConcurrentHashMap<>();
    private final StringRedisTemplate redisTemplate;

    @Value("${app.redis.enabled:false}")
    private boolean redisEnabled;


    public void addMember(String roomId, String username) {
        if (redisEnabled) {
            String key = key(roomId);
            redisTemplate.opsForSet().add(key, username);
            redisTemplate.expire(key, Duration.ofHours(24));
            return;
        }
        // 방이 없으면 새로 만들고(computeIfAbsent), 유저 닉네임을 추가(add)함
        roomUsers.computeIfAbsent(roomId, k -> new CopyOnWriteArraySet<>()).add(username);
    }


    public void removeMember(String roomId, String username) {
        if (redisEnabled) {
            redisTemplate.opsForSet().remove(key(roomId), username);
            return;
        }
        if (roomUsers.containsKey(roomId)) {
            Set<String> users = roomUsers.get(roomId);
            users.remove(username); // 명단에서 삭제


            if (users.isEmpty()) {
                roomUsers.remove(roomId);
            }
        }
    }


    public List<String> getMemberList(String roomId) {
        if (redisEnabled) {
            Set<String> users = redisTemplate.opsForSet().members(key(roomId));
            return users == null ? new ArrayList<>() : new ArrayList<>(users);
        }
        Set<String> users = roomUsers.get(roomId);

        if (users == null || users.isEmpty()) {
            return new ArrayList<>(); // null 대신 빈 리스트 반환으로 에러 방지
        }

        // 원본 Set을 보호하기 위해 복사본(new ArrayList)을 만들어 반환
        return new ArrayList<>(users);
    }

    private String key(String roomId) {
        return "chatting-alarm:room:" + roomId + ":online";
    }
}
