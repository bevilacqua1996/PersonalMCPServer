package com.bevilacqua1996.mcpServerPersonal;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

@ApplicationScoped
@Path("/")
public class GitHubToolResource {

    private static final String DOCUMENTATION_REPO_OWNER = "bevilacqua1996";
    private static final String DOCUMENTATION_REPO_NAME = "Menthoring-Documentation";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @ConfigProperty(name = "github.token", defaultValue = "")
    String githubToken;

    @Inject
    DocumentationSearchService documentationSearchService;

    @Inject
    ObjectMapper objectMapper;

    @GET
    @Path("/tools/repos")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response listReposStreamable() {
        return streamText(listReposBody(), MediaType.APPLICATION_OCTET_STREAM);
    }

    @GET
    @Path("/tools/describe")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response describeRepoStreamable(@QueryParam("name") String repoName) {
        return streamText(describeRepoBody(repoName), MediaType.APPLICATION_OCTET_STREAM);
    }

    @GET
    @Path("/tools/documentation/search")
    @Produces(MediaType.TEXT_PLAIN)
    public Response searchMenthoringDocumentationStreamable(
            @QueryParam("session") String session,
            @QueryParam("keyword") String keyword) {
        return streamText(searchDocumentationBody(session, keyword), MediaType.TEXT_PLAIN);
    }

    String searchDocumentationBody(String session, String keyword) {
        DocumentationSearchService.DocumentationSearchResult result = documentationSearchService.search(session, keyword);
        StringBuilder body = new StringBuilder();
        body.append("Session: ").append(result.session()).append('\n');
        body.append("Repository: ").append(DOCUMENTATION_REPO_OWNER).append('/').append(DOCUMENTATION_REPO_NAME).append('\n');
        body.append("Default branch: ").append(result.defaultBranch()).append('\n');
        body.append("Keyword: ").append(result.keyword()).append('\n');
        body.append("Documentation files scanned: ").append(result.documentationFilesScanned()).append("\n\n");

        if (result.message() != null) {
            body.append(result.message()).append('\n');
            return body.toString();
        }

        body.append("Relevant documentation excerpts:\n");
        for (DocumentationSearchService.DocumentationMatch match : result.matches()) {
            body.append("- ").append(match.path()).append('\n');
            if (!match.headings().isEmpty()) {
                body.append("  Headings: ").append(String.join(" | ", match.headings())).append('\n');
            }
            for (String snippet : match.snippets()) {
                body.append("  ").append(snippet).append('\n');
            }
            body.append('\n');
        }
        return body.toString();
    }

    String listReposBody() {
        if (!isGitHubTokenConfigured(githubToken)) {
            return "GitHub token not set. Please set github.token in application.properties.\n";
        }
        return fetchGitHubText("https://api.github.com/user/repos");
    }

    String describeRepoBody(String repoName) {
        String username = getGitHubUsername(githubToken).orElse("");
        if (username.isBlank()) {
            return "GitHub token not set or invalid. Please configure github.token.\n";
        }
        return fetchGitHubText("https://api.github.com/repos/" + username + "/" + repoName);
    }

    private Response streamText(String text, String mediaType) {
        StreamingOutput stream = outputStream -> {
            try (OutputStream out = outputStream) {
                out.write(text.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        };
        return Response.ok(stream).type(mediaType).build();
    }

    private String fetchGitHubText(String url) {
        try {
            HttpRequest request = createRequest(url);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                return "GitHub request failed with status " + response.statusCode() + "\n" + response.body() + "\n";
            }
            return response.body();
        } catch (Exception ex) {
            return "GitHub request failed: " + ex.getMessage() + "\n";
        }
    }

    private HttpRequest createRequest(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "PersonalMCPServer")
                .header("Authorization", "Bearer " + githubToken)
                .GET()
                .build();
    }

    private Optional<String> getGitHubUsername(String token) {
        if (!isGitHubTokenConfigured(token)) {
            return Optional.empty();
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.github.com/user"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "PersonalMCPServer")
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                return Optional.empty();
            }
            JsonNode body = objectMapper.readTree(response.body());
            if (body.hasNonNull("login")) {
                return Optional.of(body.get("login").asText(""));
            }
        } catch (Exception ignored) {
            // fall through to empty optional
        }
        return Optional.empty();
    }

    private boolean isGitHubTokenConfigured(String token) {
        return token != null
                && !token.isBlank()
                && !"TOKEN".equals(token)
                && !"YOUR_GITHUB_TOKEN_HERE".equals(token);
    }
}
