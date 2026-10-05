package com.springlensai.server.service.indexing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CodeFileFilterTest {

    private final CodeFileFilter filter = new CodeFileFilter();
    private static final long MAX = 100_000;

    @Test
    void acceptsSourceFiles() {
        assertTrue(filter.isEligible("src/main/java/App.java", 1000, MAX));
        assertTrue(filter.isEligible("README.md", 1000, MAX));
        assertTrue(filter.isEligible("Dockerfile", 1000, MAX));
    }

    @Test
    void rejectsNoiseAndSecrets() {
        assertFalse(filter.isEligible("node_modules/x/index.js", 10, MAX));
        assertFalse(filter.isEligible("package-lock.json", 10, MAX));
        assertFalse(filter.isEligible(".env", 10, MAX));
        assertFalse(filter.isEligible("public/app.min.js", 10, MAX));
        assertFalse(filter.isEligible("logo.png", 10, MAX));
        assertFalse(filter.isEligible("src/Big.java", MAX + 1, MAX));
    }

    @Test
    void detectsLanguage() {
        assertEquals("ts", filter.detectLanguage("a/b/c.TS"));
        assertEquals("dockerfile", filter.detectLanguage("docker/Dockerfile"));
        assertEquals("text", filter.detectLanguage("LICENSE"));
    }
}
