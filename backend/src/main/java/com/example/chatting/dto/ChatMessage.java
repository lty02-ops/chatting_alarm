package com.example.chatting.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatMessage {
    private Long id;
    private MessageType type;
    private String roomId;
    private String content;
    private String sender;
    private LocalDateTime timestamp;
    private String attachmentUrl;
    private String attachmentName;
    private String attachmentType;


    public enum MessageType {
        CHAT, JOIN, INVITE, LEAVE, READ
    }
}
