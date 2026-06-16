package com.example.hrai.ai.mcp.dto;

public record CancelPendingMcpRequest(
        String toolToken,
        String sessionId,
        String pendingId,
        Integer expectedVersion
) {
}
