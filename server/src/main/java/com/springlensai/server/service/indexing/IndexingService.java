package com.springlensai.server.service.indexing;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

import com.springlensai.server.entity.IndexStatus;
import com.springlensai.server.entity.Repository;
import com.springlensai.server.exceptions.BadRequestException;
import com.springlensai.server.exceptions.NotFoundException;
import com.springlensai.server.repository.RepositoryRepository;
import com.springlensai.server.service.UserService;
import com.springlensai.server.service.ai.AiErrors;
import com.springlensai.server.service.ai.RagSettings;
import com.springlensai.server.service.github.GitHubRateLimiter;
import com.springlensai.server.service.github.GithubApiClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class IndexingService {

    private static final int VECTOR_BATCH_SIZE = 16;
    private static final int PROGRESS_EVERY_N_FILES = 5;

    private final RepositoryRepository repositoryRepository;
    private final UserService userService;
    private final GithubApiClient gitHubApiClient;
    private final CodeFileFilter fileFilter;
    private final CodeChunker codeChunker;
    private final GitHubRateLimiter rateLimiter;
    private final VectorStore vectorStore;

    @Value("${app.indexing.max-file-bytes:102400}")
    private long maxFileBytes;

    @Value("${app.indexing.max-files:400}")
    private int maxFiles;

    @Value("${app.indexing.embed-delay-ms:250}")
    private long embedDelayMs;

    @Value("${app.indexing.embed-max-retries:5}")
    private int embedMaxRetries;

    @EventListener(ApplicationReadyEvent.class)
    public void failInterruptedJobs() {
        try {
            List<Repository> stuck = repositoryRepository.findByIndexStatus(IndexStatus.INDEXING);
            for(Repository repo : stuck) {
                repo.setIndexStatus(IndexStatus.FAILED);
                repo.setErrorMessage("Indexing was interrupted by a server restart. Please retry.");
                repo.setUpdatedAt(Instant.now());
            }
            if(!stuck.isEmpty()) {
                repositoryRepository.saveAll(stuck);
                log.warn("Marked {} interrupted indexing job(s) as FAILED", stuck.size());
            }
        } catch(Exception ex) {
            log.warn("Could not recover interrupted indexing jobs: {}", ex.getMessage());
        }
    }

    public Repository startIndexing(UUID repoId, UUID userId) {
        Repository repo = repositoryRepository.findByIdAndUserId(repoId, userId)
                          .orElseThrow(() -> new NotFoundException("Repository not found"));

        if(repo.getIndexStatus() == IndexStatus.INDEXING) {
            throw new BadRequestException("Repository is already being indexed");
        }

        repo.setIndexStatus(IndexStatus.INDEXING);
        repo.setFilesProcessed(0);
        repo.setFilesTotal(0);
        repo.setChunkCount(0);
        repo.setErrorMessage(null);
        repo.setUpdatedAt(Instant.now());
        return repositoryRepository.save(repo);
    }

    @Async("indexingExecutor")
    public void indexAsync(UUID repoId, UUID userId) {
        try {
            doIndex(repoId, userId);
        } catch(Exception ex) {
            log.error("Indexing failed for repo {}", repoId, ex);
            markFailed(repoId, describeFailure(ex));
        }
    }

    private void doIndex(UUID repoId, UUID userId) {
        Repository repo = repositoryRepository.findById(repoId)
                          .orElseThrow(() -> new NotFoundException("Repository not found"));
        String token = userService.decryptAccessToken(userService.requiredById(userId));
        String branch = repo.getDefaultBranch();

        deleteExistingVectors(repoId.toString());

        Map<String, Object> tree = gitHubApiClient.getRepoTree(token, repo.getOwner(), repo.getName(), branch);
        if(tree != null && Boolean.TRUE.equals(tree.get("truncated"))) {
            log.warn("GitHub truncated the file tree for {}(very large repository)", repo.getFullName());
        }

        List<String> filePaths = listIndexableFiles(tree);
        if(filePaths.isEmpty()) {
            throw new BadRequestException("No indexable source files were found in this repository.");
        }
        if(filePaths.size() > maxFiles) {
            log.info("{} has {} eligible files; indexing the first {}", repo.getFullName(), filePaths.size(), maxFiles);
            filePaths = filePaths.subList(0, maxFiles);
        }

        updateProgress(repoId, filePaths.size(), 0, 0);

        List<Document> batch = new ArrayList<>();
        int processed = 0;
        int totalChunks = 0;

        for(String path : filePaths) {
            try {
                String content = gitHubApiClient.getFileContent(token, repo.getOwner(), repo.getName(), path, branch);
                List<Document> chunks = codeChunker.chunkFile(repoId.toString(), path, content);
                batch.addAll(chunks);
                totalChunks += chunks.size();
            } catch(RestClientResponseException ex) {
                if(ex.getStatusCode().value() == 401) {
                    throw ex; // token is dead - no point continuing
                }
                log.warn("Skipping file {} in {}: HTTP {}", path, repo.getFullName(), ex.getStatusCode().value());
            } catch(Exception ex) {
                log.warn("Skipping file {} in {}: {}", path, repo.getFullName(), ex.getMessage());
            }

            if(batch.size() >= VECTOR_BATCH_SIZE) {
                embedAndStore(batch);
                batch.clear();
            }

            processed++;
            if(processed % PROGRESS_EVERY_N_FILES == 0 || processed == filePaths.size()) {
                updateProgress(repoId, filePaths.size(), processed, totalChunks);
            }
            rateLimiter.pause();
        }

        if(!batch.isEmpty()) {
            embedAndStore(batch);
        }

        if(totalChunks == 0) {
            throw new BadRequestException("The files in this repository had no readable text content.");
        }

        markReady(repoId, filePaths.size(), processed, totalChunks, repo.getFullName());
    }

    private void embedAndStore(List<Document> batch) {
        int attempt = 0;
        while(true) {
            try {
                vectorStore.add(new ArrayList<>(batch));
                sleep(embedDelayMs);
                return;
            } catch(RuntimeException ex) {
                attempt++;
                if(attempt > embedMaxRetries || !AiErrors.isTransient(ex)) {
                    throw ex;
                }
                long wait = Math.min(60_000L, 2_000L *(1L <<(attempt - 1)));
                log.warn("Embedding batch failed({}). Retry {}/{} in {} ms", ex.getMessage(), attempt, embedMaxRetries, wait);
                sleep(wait);
            }
        }
    }

    private List<String> listIndexableFiles(Map<String, Object> tree) {
        if(tree == null || !(tree.get("tree") instanceof List<?> entries)) {
            return List.of();
        }

        List<String> paths = new ArrayList<>();
        for(Object entryObj : entries) {
            if(!(entryObj instanceof Map<?, ?> entry)) {
                continue;
            }
            if(!"blob".equals(String.valueOf(entry.get("type")))) {
                continue;
            }
            String path = String.valueOf(entry.get("path"));
            long size = entry.get("size") instanceof Number n ? n.longValue() : 0L;
            if(fileFilter.isEligible(path, size, maxFileBytes)) {
                paths.add(path);
            }
        }
        paths.sort(Comparator.comparingInt((String p) -> p.split("/").length).thenComparing(Comparator.naturalOrder()));
        return paths;
    }

    private void deleteExistingVectors(String repoId) {
        try {
            var filter = new FilterExpressionBuilder().eq(RagSettings.METADATA_REPO_ID, repoId).build();
            vectorStore.delete(filter);
        } catch(Exception ex) {
            log.warn("Could not delete existing vectors for repo {}: {}", repoId, ex.getMessage());
        }
    }

    private void updateProgress(UUID repoId, int total, int processed, int chunks) {
        repositoryRepository.findById(repoId).ifPresent(repo -> {
            repo.setFilesTotal(total);
            repo.setFilesProcessed(processed);
            repo.setChunkCount(chunks);
            repo.setIndexStatus(IndexStatus.INDEXING);
            repo.setErrorMessage(null);
            repo.setUpdatedAt(Instant.now());
            repositoryRepository.save(repo);
        });
    }

    private void markReady(UUID repoId, int totalFiles, int processedFiles, int totalChunks, String fullName) {
        repositoryRepository.findById(repoId).ifPresent(repo -> {
            repo.setIndexStatus(IndexStatus.READY);
            repo.setFilesTotal(totalFiles);
            repo.setFilesProcessed(processedFiles);
            repo.setChunkCount(totalChunks);
            repo.setIndexedAt(Instant.now());
            repo.setErrorMessage(null);
            repo.setUpdatedAt(Instant.now());
            repositoryRepository.save(repo);
        });
        log.info("Indexed {} files({} chunks) for {}", processedFiles, totalChunks, fullName);
    }

    public void markFailed(UUID repoId, String message) {
        repositoryRepository.findById(repoId).ifPresent(repo -> {
            repo.setIndexStatus(IndexStatus.FAILED);
            repo.setErrorMessage(message != null && message.length() > 2000
                    ? message.substring(0, 2000)
                    : message);
            repo.setUpdatedAt(Instant.now());
            repositoryRepository.save(repo);
        });
    }

    private String describeFailure(Exception ex) {
        if(ex instanceof RestClientResponseException r) {
            int status = r.getStatusCode().value();
            return switch(status) {
                case 401 -> "GitHub rejected the stored access token. Sign out and sign in again.";
                case 403, 429 -> "GitHub denied access or its rate limit was reached. Try again later.";
                case 404 -> "Repository or branch not found on GitHub. Check that you still have access.";
                case 409 -> "This repository is empty.";
                default -> "GitHub API error(HTTP " + status + ").";
            };
        }
        if(ex instanceof BadRequestException || ex instanceof NotFoundException) {
            return ex.getMessage();
        }
        if(AiErrors.isTransient(ex)) {
            return AiErrors.userMessage(ex) + " Gemini's free tier has tight limits; retry in a few minutes.";
        }
        return ex.getMessage() != null ? ex.getMessage() : "Unexpected indexing error.";
    }

    private static void sleep(long millis) {
        if(millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Indexing interrupted", e);
        }
    }
}
