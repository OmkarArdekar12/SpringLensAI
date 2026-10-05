package com.springlensai.server.service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springlensai.server.entity.User;
import com.springlensai.server.exceptions.UnauthorizedException;
import com.springlensai.server.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final TextEncryptor tokenEncryptor;

    @Transactional
    public User upsertFromGitHub(Map<String, Object> attributes, String accessToken, String scopes) {
        Long githubId = toLong(attributes.get("id"));

        String login = String.valueOf(attributes.get("login"));

        String name = attributes.get("name") != null
                      ? String.valueOf(attributes.get("name")) : login;

        String avatarUrl = attributes.get("avatar_url") != null
                           ? String.valueOf(attributes.get("avatar_url")) : null;

        String encryptedToken = tokenEncryptor.encrypt(accessToken);

        User user = userRepository.findByGithubId(githubId).orElseGet(User::new);
        user.setGithubId(githubId);
        user.setGithubUsername(login);
        user.setDisplayName(name);
        user.setAvatarUrl(avatarUrl);
        user.setAccessToken(encryptedToken);
        user.setTokenScopes(scopes);

        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public Optional<User> findById(UUID id) {
        return userRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public User requiredById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UnauthorizedException("Your session is no longer valid. Please sign in again."));
    }

    public String decryptAccessToken(User user) {
        try {
            return tokenEncryptor.decrypt(user.getAccessToken());
        } catch(RuntimeException ex) {
            throw new UnauthorizedException("Your GitHub connection must be renewed. Please sign in again.");
        }
    }

    private static Long toLong(Object value) {
        if(value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}
