package com.example.chatting.repository;

import com.example.chatting.entity.Friendship;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {
    List<Friendship> findByUserAIdOrUserBId(Long userAId, Long userBId);
    Optional<Friendship> findByUserAIdAndUserBId(Long userAId, Long userBId);
}
