package com.springlensai.server.service.github;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

@Service
public class GithubApiClient {

    private static final String API_BASE = "https://api.github.com";

    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_MAP = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {
    };

    private final RestClient http;

    public GithubApiClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.http = RestClient.builder()
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .defaultHeader(HttpHeaders.USER_AGENT, "SpringLensAI")
                .build();
    }

    public List<Map<String, Object>> listUserRepos(String accessToken) {
        List<Map<String, Object>> all = new ArrayList<>();
        int page = 1;
        while(page <= 10) {
            URI uri = URI.create(API_BASE + "/user/repos?affiliation=owner,collaborator,organization_member"
                    + "&sort=updated&per_page=100&page=" + page);
            List<Map<String, Object>> pageRepos = http.get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(LIST_MAP);
            if(pageRepos == null || pageRepos.isEmpty()) {
                break;
            }
            all.addAll(pageRepos);
            if(pageRepos.size() < 100) {
                break;
            }
            page++;
        }
        return all;
    }

    public Map<String, Object> getRepoTree(String accessToken, String owner, String repo, String branch) {
        URI uri = URI.create(API_BASE + "/repos/" + segment(owner) + "/" + segment(repo)
                + "/git/trees/" + segment(branch) + "?recursive=1");
        return http.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(MAP);
    }

    public String getFileContent(String accessToken, String owner, String repo, String path, String branch) {
        StringBuilder encodedPath = new StringBuilder();
        for(String part : path.split("/")) {
            if(part.isEmpty()) {
                continue;
            }
            if(encodedPath.length() > 0) {
                encodedPath.append('/');
            }
            encodedPath.append(segment(part));
        }
        URI uri = URI.create(API_BASE + "/repos/" + segment(owner) + "/" + segment(repo)
                + "/contents/" + encodedPath
                + "?ref=" + UriUtils.encodeQueryParam(branch, StandardCharsets.UTF_8));

        Map<String, Object> body = http.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(MAP);
        if(body == null) {
            return null;
        }
        Object encoding = body.get("encoding");
        Object content = body.get("content");
        if(content == null) {
            return null;
        }
        if("base64".equals(String.valueOf(encoding))) {
            String raw = String.valueOf(content).replaceAll("\\s", "");
            return new String(Base64.getDecoder().decode(raw), StandardCharsets.UTF_8);
        }
        return String.valueOf(content);
    }

    public void revokeAuthorization(String clientId, String clientSecret, String accessToken) {
        URI uri = URI.create(API_BASE + "/applications/" + segment(clientId) + "/grant");
        http.method(HttpMethod.DELETE)
            .uri(uri)
            .headers(headers -> headers.setBasicAuth(clientId, clientSecret))
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("access_token", accessToken))
            .retrieve()
            .toBodilessEntity();
    }

    private static String segment(String value) {
        return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
    }
}
