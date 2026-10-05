package com.springlensai.server.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.springlensai.server.entity.MessageRole;

public record ChatMessageResponse(
    UUID id,
    MessageRole role,
    String content,
    List<CitationDto> citations,
    Instant createdAt) {
        
}
