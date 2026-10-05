package com.springlensai.server.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springlensai.server.dto.IndexStatusResponse;
import com.springlensai.server.dto.RepositoryResponse;
import com.springlensai.server.entity.Repository;
import com.springlensai.server.entity.User;
import com.springlensai.server.exceptions.NotFoundException;
import com.springlensai.server.repository.RepositoryRepository;
import com.springlensai.server.service.github.GithubApiClient;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RepoService {

    private final RepositoryRepository repositoryRepository;
    private final UserService userService;
    private final GithubApiClient gitHubApiClient;

    private final ConcurrentHashMap<UUID, Object> syncLocks = new ConcurrentHashMap<>();

    public List<RepositoryResponse> syncAndListRepos(UUID userId) {
        User user = userService.requiredById(userId);
        String token = userService.decryptAccessToken(user);

        synchronized(syncLocks.computeIfAbsent(userId, id -> new Object())) {
            List<Map<String, Object>> remoteRepos = gitHubApiClient.listUserRepos(token);
            List<Repository> saved = new ArrayList<>();

            for(Map<String, Object> remote : remoteRepos) {
                Long githubRepoId = toLong(remote.get("id"));
                Repository repo = repositoryRepository
                                    .findByUserIdAndGithubRepoId(userId, githubRepoId)
                                    .orElseGet(Repository::new);

                String fullName = String.valueOf(remote.get("full_name"));
                String[] parts = fullName.split("/", 2);

                repo.setUserId(userId);
                repo.setGithubRepoId(githubRepoId);
                repo.setOwner(parts.length > 0 ? parts[0] : "");
                repo.setName(parts.length > 1 ? parts[1] : String.valueOf(remote.get("name")));
                repo.setFullName(fullName);
                repo.setPrivate(Boolean.TRUE.equals(remote.get("private")));
                repo.setDefaultBranch(remote.get("default_branch") != null
                                        ? String.valueOf(remote.get("default_branch")) : "main");
                repo.setLanguage(remote.get("language") != null ? String.valueOf(remote.get("language")) : null);
                repo.setHtmlUrl(remote.get("html_url") != null ? String.valueOf(remote.get("html_url")) : null);
                repo.setDescription(remote.get("description") != null ? String.valueOf(remote.get("description")) : null);
                repo.setUpdatedAt(Instant.now());
                if(repo.getOwner().isBlank() && remote.get("owner") instanceof Map<?, ?> ownerMap
                        && ownerMap.get("login") != null) {
                    repo.setOwner(String.valueOf(ownerMap.get("login")));
                }
                saved.add(repositoryRepository.save(repo));
            }

            return saved.stream()
                        .sorted((a, b) -> a.getFullName().compareToIgnoreCase(b.getFullName()))
                        .map(this::toResponse)
                        .toList();
        }
    }

    @Transactional(readOnly = true)
    public List<RepositoryResponse> listStored(UUID userId) {
        return repositoryRepository.findByUserIdOrderByFullNameAsc(userId).stream()
                                   .map(this::toResponse)
                                   .toList();
    }

    @Transactional(readOnly = true)
    public Repository requireOwned(UUID repoId, UUID userId) {
        return repositoryRepository.findByIdAndUserId(repoId, userId)
                                   .orElseThrow(() -> new NotFoundException("Repository not found"));
    }

    @Transactional(readOnly = true)
    public IndexStatusResponse status(UUID repoId, UUID userId) {
        Repository repo = requireOwned(repoId, userId);
        return new IndexStatusResponse(
                                        repo.getId(),
                                        repo.getIndexStatus(),
                                        repo.getFilesTotal(),
                                        repo.getFilesProcessed(),
                                        repo.getChunkCount(),
                                        repo.getIndexedAt(),
                                        repo.getErrorMessage());
    }

    public RepositoryResponse toResponse(Repository repo) {
        return new RepositoryResponse(repo.getId(),
                                      repo.getGithubRepoId(),
                                      repo.getOwner(),
                                      repo.getName(),
                                      repo.getFullName(),
                                      repo.isPrivate(),
                                      repo.getDefaultBranch(),
                                      repo.getLanguage(),
                                      repo.getHtmlUrl(),
                                      repo.getDescription(),
                                      repo.getIndexStatus(),
                                      repo.getIndexedAt(),
                                      repo.getChunkCount(),
                                      repo.getFilesTotal(),
                                      repo.getFilesProcessed(),
                                      repo.getErrorMessage());
    }

    private static Long toLong(Object value) {
        if(value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}
