package com.example.hrai.ai.multiagent;

/**
 * 假期子 Agent 的结构化输出。
 *
 * @param success 最后一次 MCP Tool 调用是否成功
 * @param leaveBalance 假期余额 Tool 的原始数据
 * @param pending create_leave_pending 返回的待确认申请；无写操作时为空
 * @param pendingId 待确认申请 ID，供前端和日志定位
 * @param traceId 最后一次 MCP Tool 调用的追踪 ID
 */
public record LeaveAgentResult(
        boolean success,
        Object leaveBalance,
        Object pending,
        String pendingId,
        String traceId
) {
}
