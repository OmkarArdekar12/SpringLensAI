package com.springlensai.server.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.springlensai.server.dto.DeleteAccountRequest;
import com.springlensai.server.security.AppUserPrincipal;
import com.springlensai.server.security.CurrentUser;
import com.springlensai.server.service.AccountService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {

    private final CurrentUser currentUser;
    private final AccountService accountService;

    @Value("${server.servlet.session.cookie.name:SPRINGLENSAI_SESSION}")
    private String sessionCookieName;

    @PostMapping("/delete")
    public ResponseEntity<Void> deleteAccount(
        @Valid @RequestBody DeleteAccountRequest body,
        HttpServletRequest request,
        HttpServletResponse response) {

        AppUserPrincipal principal = currentUser.require();
        accountService.deleteAccount(principal.getId(), body.confirmation());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        ResponseCookie expired = ResponseCookie.from(sessionCookieName, "")
                                               .path("/")
                                               .maxAge(0)
                                               .httpOnly(true)
                                               .secure(request.isSecure())
                                               .sameSite("Lax")
                                               .build();
        response.addHeader(HttpHeaders.SET_COOKIE, expired.toString());

        return ResponseEntity.noContent().build();
    }
}
