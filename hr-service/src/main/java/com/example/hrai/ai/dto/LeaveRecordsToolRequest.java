package com.example.hrai.ai.dto;

import lombok.Data;

@Data
public class LeaveRecordsToolRequest {

    private String employeeName;

    private Integer year;

    private String leaveType;
}
