package com.springlensai.server.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.springlensai.server.dto.ChatMessageResponse;
import com.springlensai.server.dto.ChatSessionResponse;
import com.springlensai.server.dto.CreateChatSessionRequest;
import com.springlensai.server.entity.ChatMessage;
import com.springlensai.server.entity.ChatSession;
import com.springlensai.server.entity.IndexStatus;
import com.springlensai.server.entity.MessageRole;
import com.springlensai.server.entity.Repository;
import com.springlensai.server.exceptions.BadRequestException;
import com.springlensai.server.exceptions.NotFoundException;
import com.springlensai.server.repository.ChatMessageRepository;
import com.springlensai.server.repository.ChatSessionRepository;
import com.springlensai.server.service.ai.ChatPromptBuilder;
import com.springlensai.server.service.ai.ChatStreamHandler;
import com.springlensai.server.service.ai.CitationMapper;
import com.springlensai.server.service.ai.CodeContextRetriever;
import com.springlensai.server.service.ai.RagSettings;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final RepoService repoService;
    private final CodeContextRetriever codeContextRetriever;
    private final ChatPromptBuilder chatPromptBuilder;
    private final ChatStreamHandler chatStreamHandler;
    private final CitationMapper citationMapper;

    @Transactional
    public ChatSessionResponse createSession(UUID userId, CreateChatSessionRequest request) {
        Repository repo = repoService.requireOwned(request.repositoryId(), userId);
        if(repo.getIndexStatus() != IndexStatus.READY) {
            throw new BadRequestException("Repository must be indexed before chatting");
        }

        String title = request.title() != null && !request.title().isBlank()
                       ? request.title() : "Chat with " + repo.getFullName();

        ChatSession session = ChatSession.builder()
                                         .userId(userId)
                                         .repositoryId(repo.getId())
                                         .title(title)
                                         .build();
        session = chatSessionRepository.save(session);
        return toSessionResponse(session);
    }

    @Transactional(readOnly = true)
    public List<ChatSessionResponse> listSessions(UUID userId, UUID repositoryId) {
        repoService.requireOwned(repositoryId, userId);
        return chatSessionRepository
                    .findByUserIdAndRepositoryIdOrderByCreatedAtDesc(userId, repositoryId)
                    .stream()
                    .map(this::toSessionResponse)
                    .toList();
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getMessages(UUID userId, UUID sessionId) {
        ChatSession session = requireSession(userId, sessionId);
        return chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId()).stream()
                                    .map(this::toMessageResponse)
                                    .toList();
    }

    @Transactional(readOnly = true)
    public ChatSession requireSession(UUID userId, UUID sessionId) {
        return chatSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new NotFoundException("Chat session not found"));
    }

    public SseEmitter streamReply(UUID userId, UUID sessionId, String userContent) {
        ChatSession session = requireSession(userId, sessionId);
        Repository repo = repoService.requireOwned(session.getRepositoryId(), userId);
        if(repo.getIndexStatus() != IndexStatus.READY) {
            throw new BadRequestException("Repository is not ready for chat");
        }

        List<ChatMessage> all = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
        List<ChatMessage> history = all.subList(Math.max(0, all.size() - RagSettings.HISTORY_MESSAGES), all.size());

        ChatMessage userMessage = chatMessageRepository.save(ChatMessage.builder()
                                                       .sessionId(session.getId())
                                                       .role(MessageRole.USER)
                                                       .content(userContent)
                                                       .build());

        if(all.isEmpty()) {
            String title = userContent.replaceAll("\\s+", " ").trim();
            session.setTitle(title.length() > 60 ? title.substring(0, 57) + "..." : title);
            chatSessionRepository.save(session);
        }

        var retrievedContext = codeContextRetriever.retrieve(repo.getId(), userContent);

        String systemPrompt = chatPromptBuilder.systemPrompt(repo.getFullName());
        String userPrompt = chatPromptBuilder.userPrompt(retrievedContext.contextText(), userContent, history);

        return chatStreamHandler.stream(
                                        session.getId(),
                                        toMessageResponse(userMessage),
                                        retrievedContext.citations(),
                                        systemPrompt,
                                        userPrompt);
    }

    private ChatSessionResponse toSessionResponse(ChatSession session) {
        return new ChatSessionResponse(
                                        session.getId(),
                                        session.getRepositoryId(),
                                        session.getTitle(),
                                        session.getCreatedAt());
    }

    private ChatMessageResponse toMessageResponse(ChatMessage message) {
        return new ChatMessageResponse(
                                        message.getId(),
                                        message.getRole(),
                                        message.getContent(),
                                        citationMapper.fromJson(message.getCitations()),
                                        message.getCreatedAt());
    }
}
