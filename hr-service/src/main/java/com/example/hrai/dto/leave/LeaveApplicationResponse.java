package com.example.hrai.dto.leave;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeaveApplicationResponse {

    private String applyNo;

    private String employeeName;

    private String leaveType;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String reason;

    private String status;

    private LocalDateTime createdAt;
}
