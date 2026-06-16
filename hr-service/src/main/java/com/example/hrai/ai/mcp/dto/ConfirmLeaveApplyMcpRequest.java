package com.example.hrai.ai.mcp.dto;

public record ConfirmLeaveApplyMcpRequest(
        String toolToken,
        String sessionId,
        String pendingId,
        Integer expectedVersion,
        String idempotencyKey
) {
}
