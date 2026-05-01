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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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

    public DocumentationSearchResult search(List<String> keyWords) {
        List<String> normalizedKeywords = normalizeKeywords(keyWords);
        DocumentationCache current = ensureCache();

        if (normalizedKeywords.isEmpty()) {
            return new DocumentationSearchResult(
                    normalizedKeywords,
                    current.defaultBranch(),
                    current.documents().size(),
                    List.of(),
                    current.isReady(),
                    "Please provide at least one keyword to orient the search.");
        }

        List<DocumentationMatch> matches = searchCachedDocumentation(current, normalizedKeywords);
        String message = null;
        if (matches.isEmpty()) {
            message = "No direct keyword matches were found in the documentation.\n"
                    + "You can try a broader keyword, a related term, or refine the list of up to five keywords.";
        }

        return new DocumentationSearchResult(
                normalizedKeywords,
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

    private List<DocumentationMatch> searchCachedDocumentation(DocumentationCache current, List<String> keyWords) {
        LinkedHashMap<String, DocumentationMatchAccumulator> matches = new LinkedHashMap<>();

        for (DocumentationDocument document : current.documents()) {
            for (String keyWord : keyWords) {
                String keywordLower = keyWord.toLowerCase(Locale.ROOT);
                List<String> snippets = extractSnippets(document.content(), keywordLower);
                if (snippets.isEmpty()) {
                    continue;
                }

                DocumentationMatchAccumulator accumulator = matches.computeIfAbsent(
                        document.path(),
                        path -> new DocumentationMatchAccumulator(path, document.headings()));
                accumulator.addSnippets(snippets);
                accumulator.addScore(countOccurrences(document.content().toLowerCase(Locale.ROOT), keywordLower));
            }
        }

        return matches.values().stream()
                .map(DocumentationMatchAccumulator::toMatch)
                .sorted(Comparator
                        .comparingInt(DocumentationMatch::score)
                        .reversed()
                        .thenComparing(DocumentationMatch::path))
                .limit(10)
                .collect(Collectors.toList());
    }

    private List<String> normalizeKeywords(List<String> keyWords) {
        if (keyWords == null || keyWords.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String keyWord : keyWords) {
            if (keyWord == null) {
                continue;
            }
            String trimmed = keyWord.trim();
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
            if (normalized.size() == 5) {
                break;
            }
        }

        return List.copyOf(normalized);
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
            List<String> keyWords,
            String defaultBranch,
            int documentationFilesScanned,
            List<DocumentationMatch> matches,
            boolean cacheReady,
            String message) {
    }

    public record DocumentationMatch(String path, List<String> headings, List<String> snippets, int score) {
    }

    private static final class DocumentationMatchAccumulator {
        private final String path;
        private final List<String> headings;
        private final List<String> snippets = new ArrayList<>();
        private int score;

        private DocumentationMatchAccumulator(String path, List<String> headings) {
            this.path = path;
            this.headings = headings;
        }

        private void addSnippets(List<String> newSnippets) {
            for (String snippet : newSnippets) {
                if (!snippets.contains(snippet)) {
                    snippets.add(snippet);
                }
            }
        }

        private void addScore(int additionalScore) {
            score += additionalScore;
        }

        private DocumentationMatch toMatch() {
            return new DocumentationMatch(path, headings, List.copyOf(snippets), score);
        }
    }
}
