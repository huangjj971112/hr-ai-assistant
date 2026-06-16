package com.example.hrai.ai.service;

import com.example.hrai.dto.ai.AiChatResponse;

/**
 * MCP 员工助手的模型决策入口。
 *
 * <p>实现类负责把用户消息交给大模型，并允许模型自主选择 MCP Tool。
 * 该接口刻意不暴露具体模型供应商，便于后续替换智谱或接入其他 ChatModel。</p>
 */
public interface McpModelAgent {

    AiChatResponse chat(String message, String sessionId);
}
