package com.example.hrai.ai.service;

import com.example.hrai.ai.mcp.dto.PendingLeaveMcpResult;
import com.example.hrai.ai.tool.ToolResult;
import com.example.hrai.entity.LeaveType;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PendingLeaveReplyPostProcessorTest {

    private final PendingLeaveReplyPostProcessor postProcessor = new PendingLeaveReplyPostProcessor();

    @Test
    void shouldBuildConfirmationReplyWhenPendingLeaveCreated() {
        Map<String, ToolResult<Object>> toolResults = new LinkedHashMap<>();
        toolResults.put("query_leave_balance", ToolResult.success("ok",
                Map.of("annualLeaveBalance", 5, "sickLeaveBalance", 3)));
        toolResults.put("create_leave_pending", ToolResult.success("ok", new PendingLeaveMcpResult(
                "PENDING",
                "张三",
                LeaveType.ANNUAL,
                "2026-06-29 09:00:00",
                "2026-06-29 18:00:00",
                "个人事务",
                false,
                "2026-06-28 10:00:00",
                "2026-06-28 10:10:00",
                "aae5-very-secret-71d7",
                1,
                "PENDING_CONFIRMATION"
        )));

        String reply = postProcessor.refine("您的年假余额为 5 天。", toolResults);

        assertThat(reply)
                .contains("待确认")
                .contains("请假类型：年假")
                .contains("请假时间：2026-06-29 09:00:00 至 2026-06-29 18:00:00")
                .contains("您的年假余额为 5 天")
                .contains("确认")
                .contains("取消")
                .doesNotContain("个人事务")
                .doesNotContain("aae5-very-secret-71d7");
    }

    @Test
    void shouldKeepModelReplyWhenPendingToolFailed() {
        String modelReply = "工具失败，请稍后重试。";

        String reply = postProcessor.refine(modelReply, Map.of(
                "create_leave_pending", ToolResult.failure("MCP_TOOL_CALL_FAILED", "工具调用失败")
        ));

        assertThat(reply).isEqualTo(modelReply);
    }
}
