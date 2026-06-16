package com.example.hrai.ai.dto;

import java.time.LocalDate;

public record AttendanceAgentToolRequest(
        String employeeName,
        LocalDate startDate,
        LocalDate endDate
) {
}
