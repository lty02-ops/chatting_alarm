package com.example.chatting.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class PresenceService {
    private static final Duration ONLINE_TIMEOUT = Duration.ofSeconds(30);
    private final Map<Long, Instant> lastSeen = new ConcurrentHashMap<>();
    private final StringRedisTemplate redisTemplate;

    @Value("${app.redis.enabled:false}")
    private boolean redisEnabled;

    public void heartbeat(Long userId) {
        if (redisEnabled) {
            redisTemplate.opsForValue().set(key(userId), Instant.now().toString(), ONLINE_TIMEOUT);
            return;
        }
        lastSeen.put(userId, Instant.now());
    }

    public void offline(Long userId) {
        if (redisEnabled) {
            redisTemplate.delete(key(userId));
            return;
        }
        lastSeen.remove(userId);
    }

    public boolean isOnline(Long userId) {
        if (redisEnabled) return Boolean.TRUE.equals(redisTemplate.hasKey(key(userId)));
        Instant seen = lastSeen.get(userId);
        if (seen == null) return false;
        if (seen.plus(ONLINE_TIMEOUT).isBefore(Instant.now())) {
            lastSeen.remove(userId);
            return false;
        }
        return true;
    }

    private String key(Long userId) {
        return "chatting-alarm:presence:" + userId;
    }
}
