package com.springlensai.server.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springlensai.server.entity.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    @Modifying
    @Query("delete from ChatMessage m where m.sessionId in (select s.id from ChatSession s where s.userId = :userId)")
    int deleteAllByUserId(@Param("userId") UUID userId);
}
