package com.springlensai.server.service.ai;

public final class RagSettings {

    public static final int TOP_K_CHUNKS = 8;

    public static final long STREAM_TIMEOUT_MS = 120_000L;

    public static final String METADATA_REPO_ID = "repoId";

    public static final int HISTORY_MESSAGES = 6;

    public static final int HISTORY_MESSAGE_MAX_CHARS = 1500;

    private RagSettings() {
        
    }
}
