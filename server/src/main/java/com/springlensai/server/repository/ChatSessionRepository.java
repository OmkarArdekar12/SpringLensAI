package com.springlensai.server.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springlensai.server.entity.ChatSession;

public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {

    List<ChatSession> findByUserIdAndRepositoryIdOrderByCreatedAtDesc(UUID userId, UUID repositoryId);

    Optional<ChatSession> findByIdAndUserId(UUID id, UUID userId);

    /** Deletes all chat sessions of the user (used by account deletion). */
    @Modifying
    @Query("delete from ChatSession s where s.userId = :userId")
    int deleteAllByUserId(@Param("userId") UUID userId);
}
