# PersonalMCPServer

Streamable MCP server that exposes GitHub-aware tools through a Spring Boot application. The project wires Spring AI's MCP server and a simple `ResponseBodyEmitter`-powered controller so GitHub API responses are streamed directly to the MCP client.

## Features
- Registers a Spring AI MCP server with `STREAMABLE` protocol and the `/api/mcp` endpoint so clients can stream tool output in real time.
- Exposes three MCP tools (`list-repos`, `describe-repo`, and `menthoring`) that call GitHub API endpoints via `RestTemplate` and a `ResponseBodyEmitter`.
- Automatically resolves the authenticated GitHub username using the provided token to scope repository lookups to the current user.
- The documentation search tool scans the public `bevilacqua1996/Menthoring-Documentation` repository and returns text snippets from matching docs using up to five keywords.

## Requirements
- Java 21 (the Gradle toolchain is configured in `build.gradle`).
- Gradle wrapper (`./gradlew`) for building, running, and testing.
- GitHub personal access token with at least `read:user` and/or `repo` scope so the API calls can run against your account.

## Configuration
All runtime configuration lives in `src/main/resources/application.yaml`:

```yaml
github:
  token: "YOUR_GITHUB_TOKEN"

spring:
  ai:
    mcp:
      server:
        protocol: STREAMABLE
        name: streamable-mcp-server
        version: 1.0.0
        type: SYNC
        instructions: "This streamable server provides real-time notifications"
        resource-change-notification: true
        tool-change-notification: true
        prompt-change-notification: true
        streamable-http:
          mcp-endpoint: /api/mcp
          keep-alive-interval: 30s
```

Override the GitHub token without editing the file by passing `-Dgithub.token=TOKEN` or by setting the `GITHUB_TOKEN` environment variable before running the app.

## Running locally
1. `./gradlew bootRun`
2. The application starts on `http://localhost:8081`; the MCP stream lives at `/api/mcp` as configured by `spring.ai.mcp.server.streamable-http.mcp-endpoint`.

## Endpoints
| Path | Description |
| --- | --- |
| `GET /tools/repos` | MCP tool `list-repos` streams the authenticated user's repositories. `curl http://localhost:8081/tools/repos` lets you watch the GitHub JSON flow in real time. |
| `GET /tools/describe?name=<repo>` | MCP tool `describe-repo` streams the repository details for `<repo>` belonging to the authenticated user. |
| `GET /tools/documentation/search?keyWords=<keyword>&keyWords=<keyword>` | MCP tool `menthoring` scans the `Menthoring-Documentation` repo and streams text excerpts that match up to five keywords. |

Both endpoints return `MediaType.APPLICATION_OCTET_STREAM_VALUE` so the MCP client can process data chunk by chunk. If no GitHub token is configured, the stream immediately sends a reminder and completes.

The documentation search endpoint accepts repeated `keyWords` query parameters and ignores the old `session` field.

## Testing
- `./gradlew test` (current tests only verify that the Spring context loads).
- The documentation search path is covered by tests that exercise the `keyWords` array contract.

## Packaging
- `./gradlew clean build` produces the Spring Boot artifact in `build/libs`.

## Notes
- The `GitHubStreamController` live-streams GitHub responses with `ResponseBodyEmitter`. If you extend the project, keep the emitter open long enough for MCP clients to receive each chunk.
