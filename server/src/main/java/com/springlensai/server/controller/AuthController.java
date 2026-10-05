package com.springlensai.server.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.springlensai.server.dto.UserResponse;
import com.springlensai.server.entity.User;
import com.springlensai.server.security.AppUserPrincipal;
import com.springlensai.server.security.CurrentUser;
import com.springlensai.server.service.UserService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final CurrentUser currentUser;
    private final UserService userService;

    @GetMapping("/login-url")
    public Map<String, String> loginUrl() {
        return Map.of("url", "/oauth2/authorization/github");
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me() {
        AppUserPrincipal principal = currentUser.require();
        User user = userService.requiredById(principal.getId());
        return ResponseEntity.ok(new UserResponse(
                                 user.getId(),
                                 user.getGithubId(),
                                 user.getGithubUsername(),
                                 user.getDisplayName(),
                                 user.getAvatarUrl()));
    }
}
