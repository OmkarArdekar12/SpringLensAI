package com.springlensai.server.service.ai;

/** Constants shared by the indexing and chat pipelines. */
public final class RagSettings {

    /** How many code chunks to fetch from the vector database per question. */
    public static final int TOP_K_CHUNKS = 8;

    /** Max time (ms) to keep an SSE stream open while the model is responding. */
    public static final long STREAM_TIMEOUT_MS = 120_000L;

    /** Metadata key stored on each embedded document (set by CodeChunker, read by the retriever). */
    public static final String METADATA_REPO_ID = "repoId";

    /** How many earlier messages of the conversation are given to the model for follow-up questions. */
    public static final int HISTORY_MESSAGES = 6;

    /** Each history message is cut to this many characters. */
    public static final int HISTORY_MESSAGE_MAX_CHARS = 1500;

    private RagSettings() {
    }
}
