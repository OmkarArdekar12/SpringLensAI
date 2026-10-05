package com.springlensai.server.service.ai;

import java.util.List;

import com.springlensai.server.dto.CitationDto;

public record RetrievedContext(
    List<CitationDto> citations,
    String contextText) {
    
}
