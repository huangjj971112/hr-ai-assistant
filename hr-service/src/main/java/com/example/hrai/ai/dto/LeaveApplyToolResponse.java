package com.example.hrai.ai.dto;

import com.example.hrai.entity.LeaveType;

import java.time.LocalDateTime;

public record LeaveApplyToolResponse(
        boolean submitted,
        String employeeName,
        LeaveType leaveType,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String reason,
        String applyNo,
        String status,
        String message
) {

    public static LeaveApplyToolResponse draft(
            String employeeName,
            LeaveType leaveType,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String reason
    ) {
        return new LeaveApplyToolResponse(
                false, employeeName, leaveType, startTime, endTime, reason,
                null, "DRAFT", "请假申请草稿已生成，请确认后提交"
        );
    }

}
