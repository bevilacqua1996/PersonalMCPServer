package com.bevilacqua1996.mcpServerPersonal;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@ApplicationScoped
public class DocumentationSearchService {

    private static final String DOCUMENTATION_REPO_OWNER = "bevilacqua1996";
    private static final String DOCUMENTATION_REPO_NAME = "Menthoring-Documentation";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final AtomicReference<DocumentationCache> cache = new AtomicReference<>(DocumentationCache.empty());

    @ConfigProperty(name = "github.token", defaultValue = "")
    String githubToken;

    @Inject
    ObjectMapper objectMapper;

    @PostConstruct
    void warmUp() {
        refreshCache();
    }

    public synchronized void refreshCache() {
        if (!isGitHubTokenConfigured(githubToken)) {
            cache.set(DocumentationCache.empty());
            Log.warn("GitHub token not configured; documentation cache is empty.");
            return;
        }

        try {
            String defaultBranch = getRepositoryDefaultBranch(DOCUMENTATION_REPO_OWNER, DOCUMENTATION_REPO_NAME);
            List<String> documentationPaths = getDocumentationPaths(defaultBranch);
            List<DocumentationDocument> documents = new ArrayList<>();

            for (String path : documentationPaths) {
                String content = fetchRawDocumentationFile(defaultBranch, path);
                if (content == null || content.isBlank()) {
                    continue;
                }
                documents.add(new DocumentationDocument(path, extractHeadings(content), content));
            }

            cache.set(new DocumentationCache(defaultBranch, documents));
            Log.info("Loaded Menthoring documentation cache with " + documents.size() + " files from branch " + defaultBranch);
        } catch (Exception ex) {
            Log.warn("Unable to refresh Menthoring documentation cache; keeping the previous cache.", ex);
        }
    }

    public DocumentationSearchResult search(String session, String keyword) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        DocumentationCache current = ensureCache();

        if (normalizedKeyword.isEmpty()) {
            return new DocumentationSearchResult(
                    session,
                    normalizedKeyword,
                    current.defaultBranch(),
                    current.documents().size(),
                    List.of(),
                    current.isReady(),
                    "Please provide a keyword to orient the search.");
        }

        List<DocumentationMatch> matches = searchCachedDocumentation(current, normalizedKeyword);
        String message = null;
        if (matches.isEmpty()) {
            message = "No direct keyword matches were found in the documentation.\n"
                    + "You can try a broader keyword or a related term.";
        }

        return new DocumentationSearchResult(
                session,
                normalizedKeyword,
                current.defaultBranch(),
                current.documents().size(),
                matches,
                current.isReady(),
                message);
    }

    private DocumentationCache ensureCache() {
        DocumentationCache current = cache.get();
        if (!current.isReady()) {
            refreshCache();
            current = cache.get();
        }
        return current;
    }

    private List<DocumentationMatch> searchCachedDocumentation(DocumentationCache current, String keyword) {
        String keywordLower = keyword.toLowerCase(Locale.ROOT);
        List<DocumentationMatch> matches = new ArrayList<>();

        for (DocumentationDocument document : current.documents()) {
            List<String> snippets = extractSnippets(document.content(), keywordLower);
            if (snippets.isEmpty()) {
                continue;
            }

            matches.add(new DocumentationMatch(
                    document.path(),
                    document.headings(),
                    snippets,
                    countOccurrences(document.content().toLowerCase(Locale.ROOT), keywordLower)));
        }

        return matches.stream()
                .sorted(Comparator
                        .comparingInt(DocumentationMatch::score)
                        .reversed()
                        .thenComparing(DocumentationMatch::path))
                .limit(10)
                .collect(Collectors.toList());
    }

    private String getRepositoryDefaultBranch(String owner, String repository) throws IOException, InterruptedException {
        String url = "https://api.github.com/repos/" + owner + "/" + repository;
        JsonNode body = getJson(url);
        if (body != null && body.hasNonNull("default_branch")) {
            return body.get("default_branch").asText("main");
        }
        return "main";
    }

    private List<String> getDocumentationPaths(String branch) throws IOException, InterruptedException {
        String url = "https://api.github.com/repos/" + DOCUMENTATION_REPO_OWNER + "/" + DOCUMENTATION_REPO_NAME + "/git/trees/" + branch + "?recursive=1";
        JsonNode body = getJson(url);
        if (body == null || !body.has("tree")) {
            return List.of();
        }

        List<String> paths = new ArrayList<>();
        for (JsonNode entry : body.get("tree")) {
            String type = entry.path("type").asText("");
            String path = entry.path("path").asText("");
            if ("blob".equals(type) && isDocumentationPath(path)) {
                paths.add(path);
            }
        }
        return paths;
    }

    private String fetchRawDocumentationFile(String branch, String path) throws IOException, InterruptedException {
        String encodedPath = encodePath(path);
        String url = "https://raw.githubusercontent.com/" + DOCUMENTATION_REPO_OWNER + "/" + DOCUMENTATION_REPO_NAME + "/" + branch + "/" + encodedPath;
        return getText(url).orElse(null);
    }

    private Optional<String> getText(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "PersonalMCPServer")
                .GET()
                .build();

        if (isGitHubTokenConfigured(githubToken)) {
            request = HttpRequest.newBuilder(request.uri())
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "PersonalMCPServer")
                    .header("Authorization", "Bearer " + githubToken)
                    .GET()
                    .build();
        }

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() / 100 != 2) {
            return Optional.empty();
        }
        return Optional.ofNullable(response.body());
    }

    private JsonNode getJson(String url) throws IOException, InterruptedException {
        Optional<String> body = getText(url);
        if (body.isEmpty()) {
            return null;
        }
        return objectMapper.readTree(body.get());
    }

    private boolean isDocumentationPath(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".md")
                || lower.endsWith(".markdown")
                || lower.endsWith(".mdx")
                || lower.endsWith(".txt")
                || lower.endsWith(".adoc");
    }

    private List<String> extractHeadings(String content) {
        List<String> headings = new ArrayList<>();
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                headings.add(trimmed.replaceFirst("^#+\\s*", ""));
            }
            if (headings.size() == 3) {
                break;
            }
        }
        return headings;
    }

    private List<String> extractSnippets(String content, String keywordLower) {
        List<String> snippets = new ArrayList<>();
        String[] lines = content.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.toLowerCase(Locale.ROOT).contains(keywordLower)) {
                snippets.add(buildSnippet(lines, i));
            }
            if (snippets.size() == 3) {
                break;
            }
        }
        return snippets;
    }

    private String buildSnippet(String[] lines, int index) {
        int start = Math.max(0, index - 1);
        int end = Math.min(lines.length - 1, index + 1);
        StringBuilder snippet = new StringBuilder();
        for (int i = start; i <= end; i++) {
            if (snippet.length() > 0) {
                snippet.append(" / ");
            }
            snippet.append(lines[i].trim());
        }
        return "Context: " + snippet;
    }

    private int countOccurrences(String contentLower, String keywordLower) {
        int count = 0;
        int index = 0;
        while ((index = contentLower.indexOf(keywordLower, index)) != -1) {
            count++;
            index += keywordLower.length();
        }
        return count;
    }

    private String encodePath(String path) {
        return java.util.Arrays.stream(path.split("/"))
                .map(segment -> java.net.URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"))
                .collect(Collectors.joining("/"));
    }

    private boolean isGitHubTokenConfigured(String token) {
        return token != null
                && !token.isBlank()
                && !"TOKEN".equals(token)
                && !"YOUR_GITHUB_TOKEN_HERE".equals(token);
    }

    public record DocumentationDocument(String path, List<String> headings, String content) {
    }

    public record DocumentationCache(String defaultBranch, List<DocumentationDocument> documents) {
        static DocumentationCache empty() {
            return new DocumentationCache("main", List.of());
        }

        boolean isReady() {
            return documents != null && !documents.isEmpty();
        }
    }

    public record DocumentationSearchResult(
            String session,
            String keyword,
            String defaultBranch,
            int documentationFilesScanned,
            List<DocumentationMatch> matches,
            boolean cacheReady,
            String message) {
    }

    public record DocumentationMatch(String path, List<String> headings, List<String> snippets, int score) {
    }
}
