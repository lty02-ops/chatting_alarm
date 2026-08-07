package com.example.chatting.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;


@Entity
@Getter // 모든 필드의 Getter 메서드 자동 생성
@Setter // 모든 필드의 Setter 메서드 자동 생성
@EntityListeners(AuditingEntityListener.class)
public class ChatMessageEntity {

    @Id // 이 필드가 테이블의 기본키(Primary Key)임을 나타냄
    @GeneratedValue(strategy = GenerationType.IDENTITY) // DB가 자동으로 숫자를 1, 2, 3... 올려가며 생성 (Auto Increment)
    private Long id;

    private String roomId;    // 메시지가 속한 채팅방의 고유 ID
    private String sender;    // 메시지를 보낸 사람의 닉네임 또는 ID
    private String content;   // 실제 대화 내용
    @Column(name = "attachment_url", length = 1000)
    private String attachmentUrl;
    @Column(name = "attachment_name", length = 255)
    private String attachmentName;
    @Column(name = "attachment_type", length = 100)
    private String attachmentType;
    private int unreadCount;  // 아직 메시지를 읽지 않은 사람의 수 (안 읽음 표시용)


    private String type;


    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime timestamp; // 메시지 전송 시간
}
