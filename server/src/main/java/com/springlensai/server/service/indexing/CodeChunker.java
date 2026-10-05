package com.springlensai.server.service.indexing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.springlensai.server.service.ai.RagSettings;

/**
 * Splits a source file into overlapping, line-aligned chunks.
 *
 * Differences from the tutorial (which used Spring AI's TokenTextSplitter):
 * - Chunks follow line boundaries, so each chunk knows its startLine/endLine. Those numbers power
 *   the clickable "file:line" citations (the tutorial's citations always had null lines).
 * - app.indexing.chunk-overlap is honoured (the tutorial ignored it).
 */
@Component
public class CodeChunker {

    /** Lines longer than this (minified code, data blobs) are cut. */
    private static final int MAX_LINE_CHARS = 2000;

    private final int chunkSize;
    private final int overlap;
    private final CodeFileFilter fileFilter;

    public CodeChunker(
            @Value("${app.indexing.chunk-size:800}") int chunkSize,
            @Value("${app.indexing.chunk-overlap:100}") int chunkOverlap,
            CodeFileFilter fileFilter) {
        this.chunkSize = Math.max(200, chunkSize);
        this.overlap = Math.max(0, Math.min(chunkOverlap, this.chunkSize / 2));
        this.fileFilter = fileFilter;
    }

    public List<Document> chunkFile(String repoId, String filePath, String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        String language = fileFilter.detectLanguage(filePath);
        String[] lines = content.split("\\R");
        List<Document> documents = new ArrayList<>();

        int start = 0;
        int chunkIndex = 0;
        while (start < lines.length) {
            StringBuilder body = new StringBuilder();
            int size = 0;
            int end = start; // exclusive

            while (end < lines.length) {
                String line = lines[end];
                if (line.length() > MAX_LINE_CHARS) {
                    line = line.substring(0, MAX_LINE_CHARS);
                }
                int added = line.length() + 1;
                // Always take at least one line so we make progress.
                if (size > 0 && size + added > chunkSize) {
                    break;
                }
                body.append(line).append('\n');
                size += added;
                end++;
            }

            if (!body.toString().isBlank()) {
                int startLine = start + 1;
                int endLine = end; // 1-based inclusive
                String text = "// File: " + filePath + " (lines " + startLine + "-" + endLine + ")\n" + body;
                documents.add(new Document(text,
                        metadata(repoId, filePath, language, startLine, endLine, chunkIndex++)));
            }

            if (end >= lines.length) {
                break;
            }

            // Step back so the next chunk repeats roughly `overlap` characters of context.
            int next = end;
            int overlapSize = 0;
            while (next > start + 1 && overlapSize < overlap) {
                next--;
                overlapSize += lines[next].length() + 1;
            }
            start = next;
        }
        return documents;
    }

    private static Map<String, Object> metadata(
            String repoId, String filePath, String language, int startLine, int endLine, int chunkIndex) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(RagSettings.METADATA_REPO_ID, repoId);
        metadata.put("filePath", filePath);
        metadata.put("language", language);
        metadata.put("startLine", startLine);
        metadata.put("endLine", endLine);
        metadata.put("chunkIndex", chunkIndex);
        return metadata;
    }
}
