package com.springlensai.server.service.ai;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.springlensai.server.dto.ChatMessageResponse;
import com.springlensai.server.dto.CitationDto;
import com.springlensai.server.entity.ChatMessage;
import com.springlensai.server.entity.MessageRole;
import com.springlensai.server.repository.ChatMessageRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatStreamHandler {

    private static final String EMPTY_REPLY = "I could not generate an answer for that. Please try rephrasing your question.";

    private final ChatModel chatModel;
    private final ChatMessageRepository chatMessageRepository;
    private final CitationMapper citationMapper;

    public SseEmitter stream(
            UUID sessionId,
            ChatMessageResponse savedUserMessage,
            List<CitationDto> citations,
            String systemPrompt,
            String userPrompt) {

        SseEmitter emitter = new SseEmitter(RagSettings.STREAM_TIMEOUT_MS);
        StringBuilder fullReply = new StringBuilder();
        AtomicBoolean finished = new AtomicBoolean(false);
        AtomicReference<Disposable> subscription = new AtomicReference<>();

        Runnable cancel =() -> {
            finished.set(true);
            Disposable d = subscription.get();
            if(d != null) {
                d.dispose();
            }
        };
        emitter.onCompletion(cancel);
        emitter.onTimeout(cancel);
        emitter.onError(ex -> cancel.run());

        try {
            emitter.send(SseEmitter.event().name("user_message").data(savedUserMessage));

            Disposable disposable = ChatClient.builder(chatModel)
                                              .build()
                                              .prompt()
                                              .system(systemPrompt)
                                              .user(userPrompt)
                                              .stream()
                                              .content()
                                              .subscribe(
                                                        token -> sendToken(emitter, fullReply, finished, token),
                                                        err -> sendError(emitter, finished, err),
                                                        () -> completeStream(emitter, sessionId, fullReply, citations, finished));
            subscription.set(disposable);
            if(finished.get()) {
                disposable.dispose();
            }
        } catch(Exception ex) {
            sendError(emitter, finished, ex);
        }

        return emitter;
    }

    private void sendToken(SseEmitter emitter, StringBuilder fullReply, AtomicBoolean finished, String token) {
        if(finished.get() || token == null) {
            return;
        }
        fullReply.append(token);
        try {
            emitter.send(SseEmitter.event().name("token").data(token, MediaType.APPLICATION_JSON));
        } catch(Exception ex) {
            finished.set(true);
            log.debug("Stopping stream, client disconnected: {}", ex.getMessage());
            throw new IllegalStateException(ex);
        }
    }

    private void completeStream(
        SseEmitter emitter,
        UUID sessionId,
        StringBuilder fullReply,
        List<CitationDto> citations,
        AtomicBoolean finished) {

        if(!finished.compareAndSet(false, true)) {
            return;
        }
        try {
            String reply = fullReply.toString().isBlank() ? EMPTY_REPLY : fullReply.toString();
            ChatMessage assistant = chatMessageRepository.save(ChatMessage.builder()
                                                         .sessionId(sessionId)
                                                         .role(MessageRole.ASSISTANT)
                                                         .content(reply)
                                                         .citations(citationMapper.toJson(citations))
                                                         .build());

            emitter.send(SseEmitter.event().name("assistant_message").data(toMessageResponse(assistant)));
            emitter.send(SseEmitter.event().name("done").data("[DONE]"));
            emitter.complete();
        } catch(Exception ex) {
            log.warn("Could not finish stream: {}", ex.getMessage());
            emitter.completeWithError(ex);
        }
    }

    private void sendError(SseEmitter emitter, AtomicBoolean finished, Throwable error) {
        if(!finished.compareAndSet(false, true)) {
            return;
        }
        log.error("Chat stream error", error);
        try {
            emitter.send(SseEmitter.event()
                   .name("error")
                   .data(Map.of("message", AiErrors.userMessage(error)), MediaType.APPLICATION_JSON));
            emitter.complete();
        } catch(Exception ex) {
            emitter.completeWithError(ex);
        }
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
