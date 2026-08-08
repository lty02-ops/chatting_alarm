package com.example.chatting.repository;

import com.example.chatting.entity.Friendship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {
    Optional<Friendship> findByUserAIdAndUserBId(Long userAId, Long userBId);

    @Query("""
            select friendship from Friendship friendship
            where friendship.status = 'ACCEPTED'
              and (friendship.userAId = :userId or friendship.userBId = :userId)
            """)
    List<Friendship> findAcceptedByUserId(@Param("userId") Long userId);

    @Query("""
            select friendship from Friendship friendship
            where friendship.status = 'PENDING'
              and friendship.requesterId <> :userId
              and (friendship.userAId = :userId or friendship.userBId = :userId)
            order by friendship.createdAt desc
            """)
    List<Friendship> findIncomingRequests(@Param("userId") Long userId);
}
