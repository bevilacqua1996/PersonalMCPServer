package com.bevilacqua1996.mcpServerPersonal;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.mcp.protocol.McpCallToolResult;
import dev.langchain4j.mcp.protocol.McpImplementation;
import dev.langchain4j.mcp.protocol.McpInitializeResult;
import dev.langchain4j.mcp.protocol.McpJsonRpcMessage;
import dev.langchain4j.mcp.protocol.McpListToolsResult;
import dev.langchain4j.mcp.protocol.McpPingResponse;
import dev.langchain4j.mcp.protocol.McpErrorResponse;

import java.util.List;
import java.util.Map;

@ApplicationScoped
@Path("/api/mcp")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class McpProtocolResource {

    private static final String PROTOCOL_VERSION = "2024-11-05";

    @Inject
    ObjectMapper objectMapper;

    @Inject
    GitHubToolResource toolResource;

    @POST
    public Response handle(String payload) {
        try {
            JsonNode request = objectMapper.readTree(payload);
            Long id = request.hasNonNull("id") ? request.get("id").asLong() : null;
            String method = request.path("method").asText("");

            if (method.isBlank()) {
                return errorResponse(id, -32600, "Invalid Request");
            }

            return switch (method) {
                case "initialize" -> jsonResponse(serialize(handleInitialize(id)));
                case "tools/list" -> jsonResponse(serialize(handleListTools(id)));
                case "tools/call" -> jsonResponse(serialize(handleCallTool(id, request.path("params"))));
                case "ping" -> jsonResponse(serialize(new McpPingResponse(id)));
                case "notifications/initialized" -> Response.noContent().build();
                default -> errorResponse(id, -32601, "Method not found");
            };
        } catch (Exception ex) {
            return errorResponse(null, -32603, ex.getMessage());
        }
    }

    private McpInitializeResult handleInitialize(Long id) {
        return new McpInitializeResult(
                id,
                new McpInitializeResult.Result(
                        PROTOCOL_VERSION,
                        new McpInitializeResult.Capabilities(new McpInitializeResult.Capabilities.Tools(false)),
                        new McpImplementation("PersonalMCPServer", "0.0.1-SNAPSHOT", "Personal MCP Server")));
    }

    private McpListToolsResult handleListTools(Long id) {
        return new McpListToolsResult(
                id,
                new McpListToolsResult.Result(
                        List.of(
                                toolSpec("list-repos", "List repo on gitHub repository", Map.<String, Object>of()),
                                toolSpec(
                                        "describe-repo",
                                        "Describe a gitHub repository",
                                        Map.of(
                                                "type", "object",
                                                "properties", Map.of(
                                                        "name", Map.of("type", "string", "description", "Repository name")),
                                                "required", List.of("name"),
                                                "additionalProperties", false)),
                                toolSpec(
                                        "menthoring",
                                        "Search the Menthoring-Documentation repository by session and keyword",
                                        Map.of(
                                                "type", "object",
                                                "properties", Map.of(
                                                        "session", Map.of("type", "string"),
                                                        "keyword", Map.of("type", "string")),
                                                "required", List.of("session", "keyword"),
                                                "additionalProperties", false)
                                )
                        ),
                        null));
    }

    private McpCallToolResult handleCallTool(Long id, JsonNode params) {
        String toolName = params.path("name").asText("");
        JsonNode arguments = params.path("arguments");
        String resultText;
        boolean error = false;

        switch (toolName) {
            case "list-repos" -> resultText = toolResource.listReposBody();
            case "describe-repo" -> resultText = toolResource.describeRepoBody(arguments.path("name").asText(""));
            case "menthoring" -> resultText = toolResource.searchDocumentationBody(
                    arguments.path("session").asText(""),
                    arguments.path("keyword").asText(""));
            default -> {
                resultText = "Unknown tool: " + toolName;
                error = true;
            }
        }

        return new McpCallToolResult(
                id,
                new McpCallToolResult.Result(
                        List.of(new McpCallToolResult.Content("text", resultText)),
                        null,
                        error ? Boolean.TRUE : Boolean.FALSE));
    }

    private Map<String, Object> toolSpec(String name, String description, Map<String, Object> inputSchema) {
        return Map.of(
                "name", name,
                "description", description,
                "inputSchema", inputSchema);
    }

    private Response errorResponse(Long id, int code, String message) {
        return jsonResponse(serialize(new McpErrorResponse(id, new McpErrorResponse.Error(code, message, null))));
    }

    private String serialize(McpJsonRpcMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to serialize MCP response", ex);
        }
    }

    private Response jsonResponse(String body) {
        return Response.ok(body, MediaType.APPLICATION_JSON).build();
    }
}
