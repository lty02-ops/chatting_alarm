package com.example.chatting.service;

import com.example.chatting.dto.ChatMessage;
import com.example.chatting.entity.ChatMessageEntity;
import com.example.chatting.entity.ChatRoomMember;
import com.example.chatting.repository.ChatMessageRepository;
import com.example.chatting.repository.ChatRoomMemberRepository;
import com.example.chatting.repository.ChatRoomRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class ChatService {
    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final NotificationService notificationService;
    private final ChatRoomRepository chatRoomRepository;


    @Transactional
    public ChatMessageEntity saveMessage(ChatMessage chatMessage) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setRoomId(chatMessage.getRoomId());
        entity.setSender(chatMessage.getSender());
        entity.setContent(chatMessage.getContent());
        entity.setAttachmentUrl(chatMessage.getAttachmentUrl());
        entity.setAttachmentName(chatMessage.getAttachmentName());
        entity.setAttachmentType(chatMessage.getAttachmentType());
        if (chatMessage.getType() != null) {
            entity.setType(chatMessage.getType().name()); // Enum 타입을 문자열로 변환하여 저장
        }
        ChatMessageEntity savedMessage = chatMessageRepository.save(entity);
        if (chatMessage.getType() == ChatMessage.MessageType.CHAT) {
            notificationService.createForChatMessage(savedMessage);
        }
        return savedMessage;
    }


    @Transactional
    public void registerMemberIfFirstTime(String roomId, String nickname) {
        // 1. 해당 방에 이미 등록된 멤버인지 조회
        Optional<ChatRoomMember> existing = chatRoomMemberRepository.findByRoomIdAndUserNickname(roomId, nickname);

        if (existing.isPresent()) {
            // 이미 존재한다면: 혹시라도 입장 시간이 누락되었다면 현재 시간으로 보정
            ChatRoomMember member = existing.get();
            if (member.getJoinedAt() == null) {
                member.setJoinedAt(LocalDateTime.now());
                chatRoomMemberRepository.saveAndFlush(member);
            }
            System.out.println("==== [DEBUG] 기존 멤버 확인됨: " + nickname + ", 시간: " + member.getJoinedAt());
        } else {
            // 신규 입장이라면: 새로운 멤버 엔티티 생성 및 현재 시간(입장 시점) 저장
            ChatRoomMember newMember = new ChatRoomMember();
            newMember.setRoomId(roomId);
            newMember.setUserNickname(nickname);
            newMember.setJoinedAt(LocalDateTime.now()); // 이 시간이 메시지 필터링의 기준점이 됨

            chatRoomMemberRepository.saveAndFlush(newMember); // 즉시 DB에 반영하여 동기화
            System.out.println("==== [DEBUG] 신규 멤버 저장 성공: " + nickname + ", 시간: " + newMember.getJoinedAt());
        }
    }


    public List<ChatMessageEntity> getMessagesForUser(String roomId, String nickname) {
        if (nickname == null || nickname.isEmpty()) return Collections.emptyList();

        // 1. 멤버 테이블에서 해당 유저의 입장 시간을 찾음
        return chatRoomMemberRepository.findByRoomIdAndUserNickname(roomId, nickname)
                .map(member -> {
                    System.out.println("==== [DEBUG] 메시지 조회 기준 시간: " + member.getJoinedAt());
                    // 2. 해당 입장 시간 이후(After)에 발생한 메시지만 리포지토리에서 조회
                    return chatMessageRepository.findByRoomIdAndTimestampAfterOrderByTimestampAsc(roomId, member.getJoinedAt());
                })
                .orElse(Collections.emptyList()); // 멤버 정보가 없으면 빈 리스트 반환
    }


    @Transactional
    public void removeMember(String roomId, String nickname) {
        chatRoomMemberRepository.deleteByRoomIdAndUserNickname(roomId, nickname);
    }

    @Transactional
    public boolean joinRoom(String roomId, String nickname) {
        if (isMember(roomId, nickname)) {
            return true;
        }

        return chatRoomRepository.findById(roomId)
                .map(room -> {
                    int maximum = room.getMaxParticipants() > 0 ? room.getMaxParticipants() : 10;
                    if (chatRoomMemberRepository.countByRoomId(roomId) >= maximum) {
                        return false;
                    }
                    registerMemberIfFirstTime(roomId, nickname);
                    return true;
                })
                .orElse(false);
    }

    public long getMemberCount(String roomId) {
        return chatRoomMemberRepository.countByRoomId(roomId);
    }

    public boolean isMember(String roomId, String nickname) {
        return chatRoomMemberRepository.findByRoomIdAndUserNickname(roomId, nickname).isPresent();
    }


    public List<String> getMemberList(String roomId) {
        return chatRoomMemberRepository.findAllByRoomId(roomId).stream()
                .map(ChatRoomMember::getUserNickname)
                .collect(Collectors.toList());
    }


}
