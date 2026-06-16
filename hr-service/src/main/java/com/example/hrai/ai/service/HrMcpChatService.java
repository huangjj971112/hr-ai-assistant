package com.example.hrai.ai.service;

import com.example.hrai.ai.multiagent.CoordinatorAgent;
import com.example.hrai.dto.ai.AiChatResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * MCP 对话入口服务。
 *
 * <p>这里只做请求规范化和入口级分流：复合薪酬影响问题交给
 * Multi-Agent Coordinator，其余问题继续委托给模型 Agent 自主选择 MCP Tool。</p>
 */
@Service
public class HrMcpChatService {

    private final McpModelAgent modelAgent;
    private final CoordinatorAgent coordinatorAgent;

    public HrMcpChatService(McpModelAgent modelAgent, CoordinatorAgent coordinatorAgent) {
        this.modelAgent = modelAgent;
        this.coordinatorAgent = coordinatorAgent;
    }

    public AiChatResponse chat(String message, String sessionId) {
        String normalizedMessage = message == null ? "" : message.trim();
        String resolvedSessionId = StringUtils.hasText(sessionId) ? sessionId.trim() : "default-session";
        if (coordinatorAgent.supports(normalizedMessage)) {
            return coordinatorAgent.chat(normalizedMessage, resolvedSessionId);
        }
        return modelAgent.chat(normalizedMessage, resolvedSessionId);
    }
}
