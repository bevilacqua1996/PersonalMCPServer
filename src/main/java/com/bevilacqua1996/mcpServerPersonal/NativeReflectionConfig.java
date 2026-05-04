package com.bevilacqua1996.mcpServerPersonal;

import io.quarkus.runtime.annotations.RegisterForReflection;
import dev.langchain4j.mcp.protocol.*;

@RegisterForReflection(targets = {
    McpJsonRpcMessage.class,
    McpInitializeResult.class,
    McpListToolsResult.class,
    McpCallToolResult.class,
    McpCallToolResult.Content.class,
    McpPingResponse.class,
    McpErrorResponse.class,
    McpImplementation.class
}, registerFullHierarchy = true)
public class NativeReflectionConfig {
}