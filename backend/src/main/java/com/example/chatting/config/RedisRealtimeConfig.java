package com.example.chatting.config;

import com.example.chatting.service.RealtimePublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RedisRealtimeConfig {

    @Bean
    @ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true")
    RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RealtimePublisher realtimePublisher) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(realtimePublisher, new ChannelTopic(RealtimePublisher.CHANNEL));
        return container;
    }
}
