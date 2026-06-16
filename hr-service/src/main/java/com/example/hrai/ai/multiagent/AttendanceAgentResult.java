package com.example.hrai.ai.multiagent;

import com.example.hrai.dto.attendance.AttendanceRecordResponse;

import java.util.List;

/**
 * 考勤子 Agent 的结构化输出。
 *
 * @param success MCP Tool 是否成功
 * @param records 原始考勤记录，供后续更精细展示使用
 * @param lateCount 从记录中提取出的迟到次数
 * @param traceId MCP Tool 返回的追踪 ID，用于关联审计日志
 */
public record AttendanceAgentResult(
        boolean success,
        List<AttendanceRecordResponse> records,
        int lateCount,
        String traceId
) {
}
