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

    /**
     * 通过 Streamable HTTP 真实调用 MCP Server 上的某个工具。
     *
     * <p>调用方传入的 {@code toolName} 决定执行哪个 MCP Tool，例如
     * {@code query_leave_policy} 或 {@code create_leave_pending}。
     * {@code request} 是工具的业务参数，不直接暴露给模型的安全字段
     * 如 toolToken、sessionId 也会由后端放在这里。</p>
     *
     * <p>注意协议参数会被包装成 {@code {"request": request}}。这是因为
     * {@code HrMcpTools} 里的工具方法都只有一个 request DTO 参数，例如
     * {@code queryAttendance(QueryAttendanceMcpRequest request)}。如果不包这一层，
     * MCP Server 端就无法把协议参数绑定到 Java DTO。</p>
     *
     * @param toolName MCP 工具名
     * @param request 工具业务参数
     * @return 反序列化后的统一 ToolResult
     */
    @Override
    public ToolResult<Object> call(String toolName, Map<String, Object> request) {
        // Streamable HTTP transport 指向本服务暴露的 /mcp 端点。
        var transport = HttpClientStreamableHttpTransport.builder(baseUrl)
                .endpoint(endpoint)
                .build();
        try (McpSyncClient client = McpClient.sync(transport)
                .clientInfo(new McpSchema.Implementation("hr-web-mcp-agent", "1.0.0"))
                .requestTimeout(Duration.ofSeconds(20))
                .initializationTimeout(Duration.ofSeconds(10))
                .build()) {
            // MCP 协议要求先 initialize，之后才能进行 tool/list、tool/call 等操作。
            client.initialize();
            /*
             * MCP Tool 的 Java 方法只有一个 request DTO，因此协议参数统一包在 request 节点下。
             * 例：query_leave_policy -> {"request": {"toolToken": "...", "question": "..."}}
             */
            McpSchema.CallToolResult result = client.callTool(
                    new McpSchema.CallToolRequest(toolName, Map.of("request", request))
            );
            // 当前 MCP Tool 统一把 ToolResult JSON 写到 TextContent 中。
            String json = result.content().stream()
                    .filter(McpSchema.TextContent.class::isInstance)
                    .map(McpSchema.TextContent.class::cast)
                    .map(McpSchema.TextContent::text)
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("MCP_EMPTY_RESULT", "MCP Tool 未返回结果"));
            // 反序列化成 ToolResult<Object>，让上层 Agent 不需要感知 MCP 原始响应格式。
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            // 不把底层 HTTP/MCP/Jackson 异常直接暴露给前端，统一转成业务错误码。
            throw new BusinessException("MCP_CLIENT_CALL_FAILED", "MCP Agent 调用失败");
        }
    }
}
