# PersonalMCPServer

Streamable MCP server that exposes GitHub-aware tools through a **Quarkus** application (recently migrated from Spring Boot). The project uses LangChain4j's MCP server to expose tools that stream GitHub API responses directly to MCP clients.

## Features
- Registers a LangChain4j MCP server with `STREAMABLE` protocol and the `/api/mcp` endpoint so clients can stream tool output in real time.
- Exposes three MCP tools (`list-repos`, `describe-repo`, and `menthoring`) that call GitHub API endpoints via REST and stream responses.
- Automatically resolves the authenticated GitHub username using the provided token to scope repository lookups to the current user.
- The documentation search tool scans the public `bevilacqua1996/Menthoring-Documentation` repository and returns text snippets from matching docs using up to five keywords.

## Requirements
- Java 21
- Gradle wrapper (`./gradlew`) for building, running, and testing
- GitHub personal access token with at least `read:user` and/or `repo` scope so the API calls can run against your account

## Configuration
All runtime configuration lives in `src/main/resources/application.properties`:

```properties
# GitHub token for API access
github.token=YOUR_GITHUB_TOKEN

# Quarkus HTTP port
quarkus.http.port=8081
```

Override the GitHub token without editing the file by passing `-Dgithub.token=TOKEN` or by setting the `GITHUB_TOKEN` environment variable before running the app.

## Running locally
1. `./gradlew quarkusDev`
2. The application starts on `http://localhost:8081`; the MCP endpoint is available at `/api/mcp`.

## Endpoints
| Path | Description |
| --- | --- |
| `GET /tools/repos` | MCP tool `list-repos` streams the authenticated user's repositories. `curl http://localhost:8081/tools/repos` lets you watch the GitHub JSON flow in real time. |
| `GET /tools/describe?name=<repo>` | MCP tool `describe-repo` streams the repository details for `<repo>` belonging to the authenticated user. |
| `GET /tools/documentation/search?keyWords=<keyword>&keyWords=<keyword>` | MCP tool `menthoring` scans the `Menthoring-Documentation` repo and streams text excerpts that match up to five keywords. |

Both endpoints return `MediaType.APPLICATION_OCTET_STREAM_VALUE` so the MCP client can process data chunk by chunk. If no GitHub token is configured, the stream immediately sends a reminder and completes.

The documentation search endpoint accepts repeated `keyWords` query parameters and ignores the old `session` field.

## Testing
- `./gradlew test` (current tests verify the Quarkus context loads).
- The documentation search path is covered by tests that exercise the `keyWords` array contract.

## Packaging
- `./gradlew build` produces the Quarkus artifact in `build/libs`.

## Notes
- The project was recently migrated from Spring Boot to Quarkus
- Uses LangChain4j MCP server implementation
