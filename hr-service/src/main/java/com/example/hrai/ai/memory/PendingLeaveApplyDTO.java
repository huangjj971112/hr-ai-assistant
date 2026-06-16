package com.example.hrai.ai.memory;

import com.example.hrai.entity.LeaveType;
import com.example.hrai.dto.leave.LeaveApplyResponse;

import java.time.LocalDateTime;

/**
 * Redis 中保存的待确认请假状态。
 *
 * <p>pendingId 标识一次申请，version 用于乐观并发控制，status 表示状态机阶段，
 * idempotencyKey 与 submittedResponse 用于保证确认提交可以安全重试。</p>
 */
public record PendingLeaveApplyDTO(
        String state,
        String employeeName,
        LeaveType leaveType,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String reason,
        boolean confirmed,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        String pendingId,
        int version,
        String status,
        String idempotencyKey,
        LeaveApplyResponse submittedResponse
) {

    public PendingLeaveApplyDTO(
            String state,
            String employeeName,
            LeaveType leaveType,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String reason,
            boolean confirmed,
            LocalDateTime createdAt,
            LocalDateTime expiresAt
    ) {
        this(state, employeeName, leaveType, startTime, endTime, reason, confirmed, createdAt, expiresAt,
                null, 0, null, null, null);
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public PendingLeaveApplyDTO withConfirmed() {
        // 每次状态转换递增 version，使旧页面或并发请求无法继续操作过期状态。
        return new PendingLeaveApplyDTO(
                state,
                employeeName,
                leaveType,
                startTime,
                endTime,
                reason,
                true,
                createdAt,
                expiresAt,
                pendingId,
                version + 1,
                "CONFIRMED",
                idempotencyKey,
                submittedResponse
        );
    }

    public PendingLeaveApplyDTO withSubmitted(String key, LeaveApplyResponse response) {
        // 保存首次提交响应，后续使用相同幂等键时直接返回，不再次调用请假业务。
        return new PendingLeaveApplyDTO(
                state, employeeName, leaveType, startTime, endTime, reason, true, createdAt, expiresAt,
                pendingId, version + 1, "SUBMITTED", key, response
        );
    }

    public PendingLeaveApplyDTO withCancelled() {
        return new PendingLeaveApplyDTO(
                state, employeeName, leaveType, startTime, endTime, reason, false, createdAt, expiresAt,
                pendingId, version + 1, "CANCELLED", idempotencyKey, submittedResponse
        );
    }
}
