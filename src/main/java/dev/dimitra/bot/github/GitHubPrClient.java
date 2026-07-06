package dev.dimitra.bot.github;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dimitra.bot.model.ChangedFile;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GitHubPrClient {
    private static final int GITHUB_PAGE_SIZE = 100;
    private static final String JSON_ACCEPT_HEADER = "application/vnd.github+json";
    private static final String DIFF_ACCEPT_HEADER = "application/vnd.github.v3.diff";

    private final String token;
    private final HttpClient http;
    private final ObjectMapper mapper;

    public GitHubPrClient(String token) {
        this.token = token;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        this.mapper = new ObjectMapper();
    }

    public PullRequestContext fetchPullRequestContext(String owner, String repo, int prNumber) throws Exception {
        String prUrl = pullRequestUrl(owner, repo, prNumber);

        String responseBody = githubGet(prUrl, JSON_ACCEPT_HEADER, 40, false);
        JsonNode pullRequestJson = mapper.readTree(responseBody);

        RepoRef head = readRepoRef(pullRequestJson, "head", owner, repo);
        RepoRef base = readRepoRef(pullRequestJson, "base", owner, repo);

        String fullDiff = githubGet(prUrl, DIFF_ACCEPT_HEADER, 60, false);
        return new PullRequestContext(head, base, fullDiff == null ? "" : fullDiff);
    }

    public List<ChangedFile> fetchAllChangedFiles(String owner, String repo, int prNumber) throws Exception {
        List<ChangedFile> allFiles = new ArrayList<>();
        int page = 1;

        while (true) {
            String url = changedFilesUrl(owner, repo, prNumber, page);

            String responseBody = githubGet(url, JSON_ACCEPT_HEADER, 40, false);
            List<ChangedFile> pageFiles = mapper.readValue(responseBody, new TypeReference<List<ChangedFile>>() {
            });

            if (pageFiles.isEmpty()) {
                break;
            }

            allFiles.addAll(pageFiles);
            if (pageFiles.size() < GITHUB_PAGE_SIZE) {
                break;
            }
            page++;
        }

        return allFiles;
    }

    public String fetchRepoFileContent(RepoRef repoRef, String path, boolean allowNotFound) throws Exception {
        if (repoRef == null || path == null || path.isBlank()) {
            return null;
        }

        String url = fileContentUrl(repoRef, path);

        String responseBody = githubGet(url, JSON_ACCEPT_HEADER, 40, allowNotFound);
        if (responseBody == null) {
            return null;
        }

        JsonNode fileJson = mapper.readTree(responseBody);
        if (!fileJson.isObject()) {
            return null;
        }

        return decodeFileContent(fileJson);
    }

    public void postIssueComment(String owner, String repo, int prNumber, String body) throws Exception {
        String issuesUrl = String.format("https://api.github.com/repos/%s/%s/issues/%d/comments", owner, repo,
                prNumber);
        String payload = mapper.writeValueAsString(Map.of("body", body));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(issuesUrl))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", JSON_ACCEPT_HEADER)
                .header("Authorization", "token " + token)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            System.err.println("[WARN] Failed to post PR comment: HTTP " + response.statusCode() + " -> "
                    + response.body());
        }
    }

    private String githubGet(String url, String accept, int timeoutSeconds, boolean allowNotFound)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Accept", accept)
                .header("Authorization", "token " + token)
                .GET()
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (allowNotFound && response.statusCode() == 404) {
            return null;
        }
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("GitHub API error: HTTP " + response.statusCode() + " -> "
                    + response.body());
        }
        return response.body();
    }

    private static String pullRequestUrl(String owner, String repo, int prNumber) {
        return String.format("https://api.github.com/repos/%s/%s/pulls/%d", owner, repo, prNumber);
    }

    private static String changedFilesUrl(String owner, String repo, int prNumber, int page) {
        return String.format(
                "https://api.github.com/repos/%s/%s/pulls/%d/files?per_page=%d&page=%d",
                owner,
                repo,
                prNumber,
                GITHUB_PAGE_SIZE,
                page);
    }

    private static String fileContentUrl(RepoRef repoRef, String path) {
        return "https://api.github.com/repos/"
                + repoRef.owner()
                + "/"
                + repoRef.repo()
                + "/contents/"
                + encodePath(path)
                + "?ref="
                + urlEncode(repoRef.sha());
    }

    private static RepoRef readRepoRef(JsonNode pullRequestJson, String side, String defaultOwner, String defaultRepo) {
        JsonNode sideJson = pullRequestJson.path(side);
        return new RepoRef(
                sideJson.path("repo").path("owner").path("login").asText(defaultOwner),
                sideJson.path("repo").path("name").asText(defaultRepo),
                sideJson.path("sha").asText(""));
    }

    private static String decodeFileContent(JsonNode fileJson) {
        String content = fileJson.path("content").asText(null);
        String encoding = fileJson.path("encoding").asText("");
        if (content != null && "base64".equalsIgnoreCase(encoding)) {
            return new String(Base64.getMimeDecoder().decode(content), StandardCharsets.UTF_8);
        }
        return null;
    }

    private static String encodePath(String path) {
        return List.of(path.split("/")).stream()
                .map(GitHubPrClient::urlEncode)
                .collect(Collectors.joining("/"));
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
