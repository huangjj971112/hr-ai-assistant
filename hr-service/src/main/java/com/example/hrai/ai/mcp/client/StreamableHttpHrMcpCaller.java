package com.example.hrai.ai.mcp.client;

import com.example.hrai.ai.tool.ToolResult;
import com.example.hrai.exception.BusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/**
 * 通过 Streamable HTTP 调用 HR MCP Server 的客户端适配器。
 *
 * <p>每次调用创建一个短生命周期 MCP Client，完成协议初始化后调用指定 Tool，
 * 再将 MCP 文本结果反序列化为统一的 {@link ToolResult}。</p>
 */
@Component
public class StreamableHttpHrMcpCaller implements HrMcpCaller {

    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String endpoint;

    public StreamableHttpHrMcpCaller(
            ObjectMapper objectMapper,
            @Value("${app.ai.mcp.base-url:http://localhost:${server.port:8091}}") String baseUrl,
            @Value("${app.ai.mcp.endpoint:/mcp}") String endpoint
    ) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.endpoint = endpoint;
    }

    @Override
    public ToolResult<Object> call(String toolName, Map<String, Object> request) {
        var transport = HttpClientStreamableHttpTransport.builder(baseUrl)
                .endpoint(endpoint)
                .build();
        try (McpSyncClient client = McpClient.sync(transport)
                .clientInfo(new McpSchema.Implementation("hr-web-mcp-agent", "1.0.0"))
                .requestTimeout(Duration.ofSeconds(20))
                .initializationTimeout(Duration.ofSeconds(10))
                .build()) {
            client.initialize();
            // MCP Tool 的 Java 方法只有一个 request DTO，因此协议参数统一包在 request 节点下。
            McpSchema.CallToolResult result = client.callTool(
                    new McpSchema.CallToolRequest(toolName, Map.of("request", request))
            );
            String json = result.content().stream()
                    .filter(McpSchema.TextContent.class::isInstance)
                    .map(McpSchema.TextContent.class::cast)
                    .map(McpSchema.TextContent::text)
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("MCP_EMPTY_RESULT", "MCP Tool 未返回结果"));
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("MCP_CLIENT_CALL_FAILED", "MCP Agent 调用失败");
        }
    }
}
