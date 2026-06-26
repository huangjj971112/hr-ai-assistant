package com.example.hrai.ai.multiagent;

/**
 * 子 Agent 的统一调用上下文。
 *
 * <p>当前只传用户原始消息和会话 ID。登录员工身份由 MCP 子 Agent 在服务端读取，
 * 不由模型或前端传入，避免身份边界被改写。</p>
 *
 * @param message 用户原始输入，供子 Agent 保留完整业务语义
 * @param sessionId 当前聊天会话 ID，用于 pending/confirm 记忆隔离
 */
public record AgentInvocationContext(
        String message,
        String sessionId
) {
}
