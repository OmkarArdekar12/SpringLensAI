package com.springlensai.server.service;

import java.util.List;
import java.util.UUID;

import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.springlensai.server.entity.IndexStatus;
import com.springlensai.server.entity.Repository;
import com.springlensai.server.entity.User;
import com.springlensai.server.exceptions.BadRequestException;
import com.springlensai.server.repository.RepositoryRepository;
import com.springlensai.server.service.ai.RagSettings;
import com.springlensai.server.service.github.GithubApiClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final UserService userService;
    private final RepositoryRepository repositoryRepository;
    private final VectorStore vectorStore;
    private final AccountDataEraser dataEraser;
    private final GithubApiClient githubApiClient;

    @Value("${spring.security.oauth2.client.registration.github.client-id}")
    private String githubClientId;

    @Value("${spring.security.oauth2.client.registration.github.client-secret}")
    private String githubClientSecret;

    public void deleteAccount(UUID userId, String confirmation) {
        User user = userService.requiredById(userId);

        if(confirmation == null || !confirmation.trim().equalsIgnoreCase(user.getGithubUsername())) {
            throw new BadRequestException("Confirmation does not match your GitHub username.");
        }
        if(repositoryRepository.existsByUserIdAndIndexStatus(userId, IndexStatus.INDEXING)) {
            throw new BadRequestException("A repository is still being indexed. Wait for it to finish, then try again.");
        }

        String githubToken = readTokenQuietly(user);

        List<Repository> repositories = repositoryRepository.findByUserIdOrderByFullNameAsc(userId);
        for(Repository repository : repositories) {
            if(repository.getIndexStatus() != IndexStatus.PENDING) {
                var filter = new FilterExpressionBuilder()
                                 .eq(RagSettings.METADATA_REPO_ID, repository.getId().toString())
                                 .build();
                vectorStore.delete(filter);
            }
        }

        dataEraser.eraseUserRows(userId);

        try {
            int removed = dataEraser.eraseLoginSessions(userId);
            log.info("Removed {} login session(s) for deleted user {}", removed, userId);
        } catch(Exception ex) {
            log.warn("Could not remove stored login sessions for user {}: {}", userId, ex.getMessage());
        }

        if(githubToken != null) {
            try {
                githubApiClient.revokeAuthorization(githubClientId, githubClientSecret, githubToken);
            } catch(Exception ex) {
                log.warn("Could not revoke GitHub authorization for deleted user {}: {}", userId, ex.getMessage());
            }
        }

        log.info("Deleted account {}({} repositories)", userId, repositories.size());
    }

    private String readTokenQuietly(User user) {
        try {
            return userService.decryptAccessToken(user);
        } catch(RuntimeException ex) {
            return null;
        }
    }
}
