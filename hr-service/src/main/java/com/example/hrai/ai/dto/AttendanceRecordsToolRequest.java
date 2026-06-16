package com.example.hrai.ai.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class AttendanceRecordsToolRequest {

    private String employeeName;

    /**
     * Kept for compatibility with the original single-day workflow.
     */
    private LocalDate attendanceDate;

    private LocalDate startDate;

    private LocalDate endDate;

    private String toolToken;
}
