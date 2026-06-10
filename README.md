# PersonalMCPServer

Streamable MCP server that exposes GitHub-aware tools through a **Quarkus** application. This project uses the [Quarkus MCP Server extension](https://github.com/quarkiverse/quarkus-mcp-server) to expose tools over the Model Context Protocol.

## Features
- Exposes tools over the **Streamable HTTP** transport at the `/api/mcp` endpoint.
- Provides three MCP tools:
  - `list-repos`: List repositories for the authenticated GitHub user.
  - `describe-repo`: Get detailed information about a specific repository.
  - `menthoring`: Search through the `bevilacqua1996/Menthoring-Documentation` repository using keywords.
- Refactored architecture using CDI beans and the `@Tool` annotation for declarative tool definitions.
- Preserves REST debug endpoints for manual testing of tool logic.

## Requirements
- Java 21
- Gradle wrapper (`./gradlew`) for building, running, and testing
- GitHub personal access token (`GITHUB_TOKEN`) with `read:user` and `repo` scopes.

## Configuration
Runtime configuration in `src/main/resources/application.properties`:

```properties
# GitHub token for API access
github.token=${GITHUB_TOKEN:TOKEN}

# Quarkus HTTP port
quarkus.http.port=8081

# MCP Server Configuration
quarkus.mcp.server.http.root-path=/api/mcp
```

## Running locally
1. `./gradlew quarkusDev`
2. The application starts on `http://localhost:8081`; the MCP endpoint is at `http://localhost:8081/api/mcp`.

## MCP Tools
- **menthoring**: Accepts a list of `keyWords` (string array). Searches documentation excerpts.
- **list-repos**: No arguments. Returns user repositories.
- **describe-repo**: Accepts a `name` (string). Returns repository details.

## Debug Endpoints
For manual debugging outside of an MCP client:
| Path | Description |
| --- | --- |
| `GET /tools/repos` | Debug endpoint for `list-repos` logic. |
| `GET /tools/describe?name=<repo>` | Debug endpoint for `describe-repo` logic. |
| `GET /tools/documentation/search?keyWords=<kw>` | Debug endpoint for `menthoring` logic. |

## Testing
- `./gradlew test`
- Tests verify both the REST debug endpoints and the JSON-RPC MCP protocol (initialize, list tools, and tool calls) using RestAssured.

## Architecture
- `GitHubToolService.java`: Shared business logic for GitHub and documentation search.
- `McpTools.java`: MCP tool definitions using `@Tool`.
- `GitHubToolResource.java`: REST resources for debugging.
- `DocumentationSearchService.java`: Documentation indexing and search logic.
