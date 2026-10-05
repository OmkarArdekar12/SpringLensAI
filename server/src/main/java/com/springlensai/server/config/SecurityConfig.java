package com.springlensai.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;

import com.springlensai.server.security.GithubOAuth2UserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final GithubOAuth2UserService gitHubOAuth2UserService;

    @Bean
    SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        AuthenticationSuccessHandler oauth2SuccessHandler,
        AuthenticationFailureHandler oauth2FailureHandler) throws Exception {

        http.cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(auth -> auth.requestMatchers("/",  
                                                                "/api/health", 
                                                                "/api/auth/login-url", 
                                                                "/actuator/health/**",
                                                                "/oauth2/**", 
                                                                "/login/oauth2/**", 
                                                                "/error")
            .permitAll()
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            .requestMatchers("/api/**").authenticated()
            .anyRequest().permitAll())
            .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .oauth2Login(oauth -> oauth.userInfoEndpoint(userInfo -> userInfo.userService(gitHubOAuth2UserService))
            .successHandler(oauth2SuccessHandler)
            .failureHandler(oauth2FailureHandler))
            .logout(logout -> logout
            .logoutUrl("/api/auth/logout")
            .logoutSuccessHandler((request, response, authentication) -> response.setStatus(HttpStatus.NO_CONTENT.value()))
            .invalidateHttpSession(true)
            .clearAuthentication(true)
            .deleteCookies("SPRINGLENSAI_SESSION"));

        return http.build();
    }

    @Bean
    AuthenticationSuccessHandler oauth2SuccessHandler(@Value("${app.frontend-url}") String frontendUrl) {
        SimpleUrlAuthenticationSuccessHandler handler = new SimpleUrlAuthenticationSuccessHandler();
        handler.setDefaultTargetUrl(frontendUrl + "/auth/callback");
        handler.setAlwaysUseDefaultTargetUrl(true);
        return handler;
    }

    @Bean
    AuthenticationFailureHandler oauth2FailureHandler(@Value("${app.frontend-url}") String frontendUrl) {
        return (request, response, exception) -> {
            log.warn("GitHub OAuth2 login failed: {}", exception.getMessage());
            response.sendRedirect(frontendUrl + "/login?error=oauth_failed");
        };
    }
}