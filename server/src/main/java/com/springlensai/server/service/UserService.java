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

    /** Create the user on first login, otherwise refresh profile + token. */
    @Transactional
    public User upsertFromGitHub(Map<String, Object> attributes, String accessToken, String scopes) {
        Long githubId = toLong(attributes.get("id"));

        String login = String.valueOf(attributes.get("login"));

        String name = attributes.get("name") != null
                ? String.valueOf(attributes.get("name"))
                : login;

        String avatarUrl = attributes.get("avatar_url") != null
                ? String.valueOf(attributes.get("avatar_url"))
                : null;

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

    /**
     * Loads the user or answers 401. A session can outlive its database row (e.g. after a database
     * reset); 401 makes the browser sign in again instead of showing an error.
     */
    @Transactional(readOnly = true)
    public User requiredById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UnauthorizedException("Your session is no longer valid. Please sign in again."));
    }

    public String decryptAccessToken(User user) {
        try {
            return tokenEncryptor.decrypt(user.getAccessToken());
        } catch (RuntimeException ex) {
            // Happens when TOKEN_ENCRYPTOR_PASSWORD / SALT changed after the token was stored.
            throw new UnauthorizedException("Your GitHub connection must be renewed. Please sign in again.");
        }
    }

    private static Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}
