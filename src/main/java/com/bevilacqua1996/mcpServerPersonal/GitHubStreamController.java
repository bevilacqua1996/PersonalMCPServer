package com.bevilacqua1996.mcpServerPersonal;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springframework.web.bind.annotation.PostMapping;

import java.io.IOException;

@RestController
@RequestMapping()
public class GitHubStreamController {

    @Value("${github.token}")
    private String githubToken;

    @McpTool(name = "list-repos", description = "List repo on gitHub repository")
    @GetMapping(value = "/tools/repos", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseBodyEmitter listReposStreamable() throws IOException {
        return streamGitHubApi("https://api.github.com/user/repos");
    }

    @McpTool(name = "describe-repo", description = "Describe a gitHub repository")
    @GetMapping(value = "/tools/describe", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseBodyEmitter describeRepoStreamable(@RequestParam("name") String repoName) throws IOException {
        String username = getGitHubUsername(githubToken);
        return streamGitHubApi("https://api.github.com/repos/" + username + "/" + repoName);
    }

    // Helper to stream GitHub API responses
    private ResponseBodyEmitter streamGitHubApi(String url) throws IOException {
        ResponseBodyEmitter emitter = new ResponseBodyEmitter();
        String token = githubToken;
        if (token == null || token.isEmpty() || token.equals("YOUR_GITHUB_TOKEN_HERE")) {
            emitter.send("GitHub token not set. Please set github.token in application.yaml.\n");
            emitter.complete();
            return emitter;
        }
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = createGitHubHeaders(token);
        restTemplate.execute(url, HttpMethod.GET, req -> req.getHeaders().addAll(headers), response -> {
            try (var reader = response.getBody()) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = reader.read(buffer)) != -1) {
                    emitter.send(new String(buffer, 0, bytesRead));
                }
            }
            emitter.complete();
            return null;
        });
        return emitter;
    }

    // Helper to create GitHub headers
    private HttpHeaders createGitHubHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.set("Accept", "application/vnd.github+json");
        return headers;
    }

    // Helper to get username from token
    private String getGitHubUsername(String token) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = createGitHubHeaders(token);
        String url = "https://api.github.com/user";
        var entity = new org.springframework.http.HttpEntity<>(headers);
        var response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        String body = response.getBody();
        if (body != null && body.contains("\"login\":")) {
            int idx = body.indexOf("\"login\":");
            int start = body.indexOf('"', idx + 9) + 1;
            int end = body.indexOf('"', start);
            return body.substring(start, end);
        }
        return "";
    }
}
