package com.example.hrai.ai.mcp.dto;

import java.time.LocalDate;

public record QueryAttendanceMcpRequest(
        String toolToken,
        LocalDate startDate,
        LocalDate endDate
) {
}
