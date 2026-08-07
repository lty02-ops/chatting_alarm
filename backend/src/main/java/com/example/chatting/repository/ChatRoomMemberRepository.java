package com.example.chatting.repository;

import com.example.chatting.entity.ChatRoomMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;


public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, Long> {


    List<ChatRoomMember> findAllByRoomId(String roomId);


    void deleteByRoomIdAndUserNickname(String roomId, String nickname);


    Optional<ChatRoomMember> findByRoomIdAndUserNickname(String roomId, String nickname);

    long countByRoomId(String roomId);

    List<ChatRoomMember> findAllByUserNickname(String nickname);
}
