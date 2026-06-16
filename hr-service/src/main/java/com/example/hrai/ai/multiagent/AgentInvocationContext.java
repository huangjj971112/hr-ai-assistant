package com.example.hrai.ai.multiagent;

/**
 * 子 Agent 的统一调用上下文。
 *
 * <p>当前只传用户原始消息和会话 ID。登录员工身份由 MCP 子 Agent 在服务端读取，
 * 不由模型或前端传入，避免身份边界被改写。</p>
 */
public record AgentInvocationContext(
        String message,
        String sessionId
) {
}
