package com.springlensai.server.service;

import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.springlensai.server.repository.ChatMessageRepository;
import com.springlensai.server.repository.ChatSessionRepository;
import com.springlensai.server.repository.RepositoryRepository;
import com.springlensai.server.repository.UserRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AccountDataEraser {

    private static final Pattern SAFE_TABLE_NAME = Pattern.compile("[A-Za-z0-9_]+");

    private final ChatMessageRepository chatMessageRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final RepositoryRepository repositoryRepository;
    private final UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${spring.session.jdbc.table-name:SPRING_SESSION}")
    private String sessionTable;

    @Transactional
    public void eraseUserRows(UUID userId) {
        chatMessageRepository.deleteAllByUserId(userId);
        chatSessionRepository.deleteAllByUserId(userId);
        repositoryRepository.deleteAllByUserId(userId);
        userRepository.deleteById(userId);
    }

    @Transactional
    public int eraseLoginSessions(UUID userId) {
        if (!SAFE_TABLE_NAME.matcher(sessionTable).matches()) {
            throw new IllegalStateException("Unexpected session table name");
        }
        return entityManager
                .createNativeQuery("DELETE FROM " + sessionTable + " WHERE principal_name = :principal")
                .setParameter("principal", userId.toString())
                .executeUpdate();
    }
}
