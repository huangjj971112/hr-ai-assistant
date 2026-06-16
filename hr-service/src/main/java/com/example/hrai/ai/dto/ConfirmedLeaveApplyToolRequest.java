package com.example.hrai.ai.dto;

public record ConfirmedLeaveApplyToolRequest(
        String sessionId,
        Boolean confirmed
) {
}
