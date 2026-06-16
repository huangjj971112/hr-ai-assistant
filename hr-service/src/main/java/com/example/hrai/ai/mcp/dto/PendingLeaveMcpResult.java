package com.example.hrai.ai.mcp.dto;

import com.example.hrai.ai.memory.PendingLeaveApplyDTO;
import com.example.hrai.entity.LeaveType;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 返回给 MCP 调用方的 pending 状态视图。
 *
 * <p>时间统一格式化为字符串，避免不同 MCP 客户端对 Java 时间类型解析不一致。</p>
 */
public record PendingLeaveMcpResult(
        String state,
        String employeeName,
        LeaveType leaveType,
        String startTime,
        String endTime,
        String reason,
        boolean confirmed,
        String createdAt,
        String expiresAt,
        String pendingId,
        int version,
        String status
) {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static PendingLeaveMcpResult from(PendingLeaveApplyDTO pending) {
        return new PendingLeaveMcpResult(
                pending.state(),
                pending.employeeName(),
                pending.leaveType(),
                format(pending.startTime()),
                format(pending.endTime()),
                pending.reason(),
                pending.isConfirmed(),
                format(pending.createdAt()),
                format(pending.expiresAt()),
                pending.pendingId(),
                pending.version(),
                pending.status()
        );
    }

    private static String format(LocalDateTime value) {
        return value == null ? null : value.format(FORMATTER);
    }
}
