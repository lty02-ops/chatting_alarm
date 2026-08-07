package com.example.chatting.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class RealtimePublisher implements MessageListener {
    public static final String CHANNEL = "chatting-alarm:realtime";

    private final SimpMessagingTemplate messagingTemplate;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.redis.enabled:false}")
    private boolean redisEnabled;

    public void publish(String destination, Object payload) {
        if (!redisEnabled) {
            messagingTemplate.convertAndSend(destination, payload);
            return;
        }

        try {
            redisTemplate.convertAndSend(CHANNEL, objectMapper.writeValueAsString(new Event(destination, payload)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to publish realtime event", exception);
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            JsonNode event = objectMapper.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
            messagingTemplate.convertAndSend(event.get("destination").asText(), event.get("payload"));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to consume realtime event", exception);
        }
    }

    private record Event(String destination, Object payload) {}
}
