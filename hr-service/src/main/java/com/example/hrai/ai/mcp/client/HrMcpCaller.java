package com.example.hrai.ai.mcp.client;

import com.example.hrai.ai.tool.ToolResult;

import java.util.Map;

/**
 * Agent 调用 MCP Server 的统一客户端接口。
 *
 * <p>上层 Agent 只需要关心 toolName 和业务参数；具体的 MCP 协议初始化、
 * HTTP 传输、结果反序列化都由实现类处理。</p>
 */
public interface HrMcpCaller {

    /**
     * 调用一个 MCP Tool。
     *
     * @param toolName MCP Server 暴露的工具名，例如 query_leave_policy
     * @param request 传给工具的业务参数，通常包含后端注入的 toolToken/sessionId
     * @return MCP Tool 返回的统一结果
     */
    ToolResult<Object> call(String toolName, Map<String, Object> request);
}
