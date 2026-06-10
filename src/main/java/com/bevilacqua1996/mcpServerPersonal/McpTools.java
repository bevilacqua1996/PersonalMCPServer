package com.bevilacqua1996.mcpServerPersonal;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class McpTools {

    @Inject
    GitHubToolService gitHubToolService;

    @Tool(name = "menthoring", description = "Search through the documentation repository for relevant information based on keywords.")
    public String menthoring(@ToolArg(description = "List of keywords to search for in the documentation") List<String> keyWords) {
        return gitHubToolService.searchDocumentationBody(keyWords);
    }

    @Tool(name = "list-repos", description = "List all repositories for the authenticated GitHub user.")
    public String listRepos() {
        return gitHubToolService.listReposBody();
    }

    @Tool(name = "describe-repo", description = "Get detailed information about a specific repository.")
    public String describeRepo(@ToolArg(description = "The name of the repository to describe") String name) {
        return gitHubToolService.describeRepoBody(name);
    }
}
