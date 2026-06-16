package com.example.hrai.ai.mcp.client;

import com.example.hrai.ai.tool.ToolResult;

import java.util.Map;

public interface HrMcpCaller {

    ToolResult<Object> call(String toolName, Map<String, Object> request);
}
