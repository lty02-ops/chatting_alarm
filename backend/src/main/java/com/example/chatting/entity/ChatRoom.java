package com.example.chatting.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.UUID;

@Entity
@Getter @Setter
@NoArgsConstructor
public class ChatRoom {
    @Id
    private String roomId;
    private String name;
    private int totalCount = 0;
    private int maxParticipants = 10;
    private String roomType = "GROUP";
    @Column(unique = true)
    private String directKey;
    private Long createdByUserId;

    public static ChatRoom create(String name, int maxParticipants) {
        ChatRoom room = new ChatRoom();
        room.roomId = UUID.randomUUID().toString();
        room.name = name;
        room.maxParticipants = maxParticipants;
        return room;
    }

    public static ChatRoom direct(String name, String directKey, Long creatorId) {
        ChatRoom room = create(name, 2);
        room.roomType = "DIRECT";
        room.directKey = directKey;
        room.createdByUserId = creatorId;
        return room;
    }

    public static ChatRoom group(String name, int maximum, Long creatorId) {
        ChatRoom room = create(name, maximum);
        room.roomType = "GROUP";
        room.createdByUserId = creatorId;
        return room;
    }
}
