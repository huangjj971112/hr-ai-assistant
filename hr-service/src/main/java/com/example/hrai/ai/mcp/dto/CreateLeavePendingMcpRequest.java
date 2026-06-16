package com.example.hrai.ai.mcp.dto;

public record CreateLeavePendingMcpRequest(
        String toolToken,
        String sessionId,
        String message
) {
}
