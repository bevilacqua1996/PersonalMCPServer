package com.bevilacqua1996.mcpServerPersonal;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

@QuarkusTest
class PersonalMcpServerTest {

    @Inject
    DocumentationSearchService documentationSearchService;

    @Test
    void documentationSearchCanBeCalled() {
        DocumentationSearchService.DocumentationSearchResult result = documentationSearchService.search(List.of("quarkus"));
        assertNotNull(result);
    }

    @Test
    void listReposEndpointReturnsBody() {
        given()
                .when()
                .get("/tools/repos")
                .then()
                .statusCode(200);
    }

    @Test
    void describeRepoEndpointReturnsBody() {
        given()
                .queryParam("name", "demo-repo")
                .when()
                .get("/tools/describe")
                .then()
                .statusCode(200);
    }

    @Test
    void documentationSearchEndpointReturnsStructuredSummary() {
        given()
                .queryParam("keyWords", "quarkus")
                .when()
                .get("/tools/documentation/search")
                .then()
                .statusCode(200)
                .body(containsString("Keywords: quarkus"))
                .body(containsString("Repository: bevilacqua1996/Menthoring-Documentation"));
    }

    @Test
    void mcpInitializeAndListToolsWork() {
        String sessionId = initialize();

        // List Tools
        given()
                .contentType("application/json")
                .accept("application/json, text/event-stream")
                .header("Mcp-Session-Id", sessionId)
                .body("""
                        {"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}
                        """)
                .when()
                .post("/api/mcp")
                .then()
                .statusCode(200)
                .body("result.tools", hasSize(3))
                .body("result.tools.name", hasItems("list-repos", "describe-repo", "menthoring"));
    }

    @Test
    void mcpToolCallReturnsTextResult() {
        String sessionId = initialize();

        given()
                .contentType("application/json")
                .accept("application/json, text/event-stream")
                .header("Mcp-Session-Id", sessionId)
                .body("""
                        {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"menthoring","arguments":{"keyWords":["quarkus"]}}}
                        """)
                .when()
                .post("/api/mcp")
                .then()
                .statusCode(200)
                .body("result.content[0].text", containsString("Keywords: quarkus"))
                .body("result.isError", equalTo(false));
    }

    private String initialize() {
        String sessionId = given()
                .contentType("application/json")
                .accept("application/json, text/event-stream")
                .body("""
                        {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test-client","version":"1.0.0"}}}
                        """)
                .when()
                .post("/api/mcp")
                .then()
                .statusCode(200)
                .body("result.protocolVersion", equalTo("2024-11-05"))
                .extract().header("Mcp-Session-Id");
        
        assertNotNull(sessionId);
        return sessionId;
    }
}
