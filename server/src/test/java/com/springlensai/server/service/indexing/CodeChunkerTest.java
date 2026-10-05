package com.springlensai.server.service.indexing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class CodeChunkerTest {

    private final CodeChunker chunker = new CodeChunker(300, 60, new CodeFileFilter());

    @Test
    void blankContentProducesNoChunks() {
        assertTrue(chunker.chunkFile("repo", "A.java", "   \n  ").isEmpty());
    }

    @Test
    void chunksKeepLineRangesAndCoverTheWholeFile() {
        StringBuilder source = new StringBuilder();
        for(int i=1; i<=120; ++i) {
            source.append("int value").append(i).append(" = ").append(i).append(";\n");
        }

        List<Document> chunks = chunker.chunkFile("repo-1", "src/Main.java", source.toString());

        assertTrue(chunks.size() > 1);
        assertEquals(1, chunks.get(0).getMetadata().get("startLine"));
        int lastEnd = (Integer) chunks.get(chunks.size() - 1).getMetadata().get("endLine");
        assertTrue(lastEnd >= 120);
        for(Document chunk : chunks) {
            assertEquals("repo-1", chunk.getMetadata().get("repoId"));
            assertEquals("src/Main.java", chunk.getMetadata().get("filePath"));
            assertEquals("java", chunk.getMetadata().get("language"));
            assertTrue(chunk.getText().startsWith("// File: src/Main.java(lines "));
        }
    }

    @Test
    void consecutiveChunksOverlapButAlwaysMakeProgress() {
        String source = "line of code number one\n".repeat(200);
        List<Document> chunks = chunker.chunkFile("r", "x.txt", source);

        int previousStart = 0;
        for(Document chunk : chunks) {
            int start =(Integer) chunk.getMetadata().get("startLine");
            assertTrue(start > previousStart, "chunk start must strictly increase");
            previousStart = start;
        }
    }

    @Test
    void veryLongSingleLineDoesNotLoopForever() {
        List<Document> chunks = chunker.chunkFile("r", "min.js", "x".repeat(50_000));
        assertFalse(chunks.isEmpty());
    }
}
