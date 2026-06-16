package com.example.hrai.ai.mcp.audit;

import java.util.Map;

/**
 * 一次 MCP Tool 调用完成后写入审计服务的上下文快照。
 *
 * <p>arguments 在持久化前还会经过统一脱敏处理。</p>
 */
public record McpToolAuditEvent(
        String traceId,
        String toolName,
        String tenantId,
        Long userId,
        String sessionId,
        String pendingId,
        String status,
        String errorCode,
        long durationMs,
        Map<String, Object> arguments
) {
}
