package com.bevilacqua1996.mcpServerPersonal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@QuarkusTest
class PersonalMcpServerTest {

    @Inject
    DocumentationSearchService documentationSearchService;

    @Inject
    GitHubToolResource toolResource;

    @Inject
    McpProtocolResource mcpProtocolResource;

    @Inject
    ObjectMapper objectMapper;

    @Test
    void documentationSearchStartsEmptyWhenTokenIsNotConfigured() {
        DocumentationSearchService.DocumentationSearchResult result = documentationSearchService.search(List.of("quarkus"));

        assertFalse(result.cacheReady());
        assertTrue(result.message() == null || result.message().contains("No direct keyword matches"));
    }

    @Test
    void listReposEndpointReturnsReminderWithoutToken() throws Exception {
        String body = readStreamingResponse(toolResource.listReposStreamable());

        assertTrue(body.contains("GitHub token not set"));
    }

    @Test
    void describeRepoEndpointReturnsReminderWithoutToken() throws Exception {
        String body = readStreamingResponse(toolResource.describeRepoStreamable("demo-repo"));

        assertTrue(body.contains("GitHub token not set"));
    }

    @Test
    void documentationSearchEndpointReturnsStructuredSummary() throws Exception {
        String body = readStreamingResponse(toolResource.searchMenthoringDocumentationStreamable(List.of("quarkus")));

        assertTrue(body.contains("Keywords: quarkus"));
        assertTrue(body.contains("Repository: bevilacqua1996/Menthoring-Documentation"));
    }

    @Test
    void mcpInitializeAndListToolsWork() throws Exception {
        JsonNode initialize = objectMapper.readTree(readResponse(
                mcpProtocolResource.handle("""
                        {"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}
                        """)));
        JsonNode initializeResult = initialize.get("result");

        assertTrue(initializeResult.get("protocolVersion").asText().equals("2024-11-05"));

        JsonNode tools = objectMapper.readTree(readResponse(
                mcpProtocolResource.handle("""
                        {"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}
                        """)));
        JsonNode toolEntries = tools.get("result").get("tools");

        assertTrue(toolEntries.size() == 3);
        assertTrue(toolEntries.toString().contains("list-repos"));
        assertTrue(toolEntries.toString().contains("describe-repo"));
        assertTrue(toolEntries.toString().contains("menthoring"));
    }

    @Test
    void mcpToolCallReturnsTextResult() throws Exception {
        String responseBody = readResponse(mcpProtocolResource.handle("""
                {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"menthoring","arguments":{"keyWords":["quarkus"]}}}
                """));
        JsonNode response = objectMapper.readTree(responseBody);

        assertTrue(response.get("result").get("content").toString().contains("Keywords: quarkus"));
        assertFalse(response.get("result").get("isError").asBoolean());
    }

    private String readResponse(Response response) throws Exception {
        Object entity = response.getEntity();
        if (entity instanceof String string) {
            return string;
        }
        if (entity instanceof StreamingOutput streamingOutput) {
            return readStreamingOutput(streamingOutput);
        }
        throw new IllegalStateException("Unsupported response entity: " + entity);
    }

    private String readStreamingResponse(Response response) throws Exception {
        return readResponse(response);
    }

    private String readStreamingOutput(StreamingOutput streamingOutput) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        streamingOutput.write(outputStream);
        return outputStream.toString(StandardCharsets.UTF_8);
    }
}
