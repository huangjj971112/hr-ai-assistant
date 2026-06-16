package com.example.hrai.service.ai;

import com.example.hrai.entity.LeaveType;

import java.time.LocalDateTime;
import java.util.List;

public record ParsedLeaveRequest(
        LeaveType leaveType,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String reason,
        List<String> missingFields
) {

    public boolean complete() {
        return missingFields.isEmpty();
    }
}
