package com.example.hrai.ai.service;

import com.example.hrai.ai.tool.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LeaveBalanceReplyPostProcessorTest {

    private final LeaveBalanceReplyPostProcessor postProcessor = new LeaveBalanceReplyPostProcessor();

    @Test
    void shouldOnlyReturnAnnualBalanceWhenUserOnlyAsksAnnualLeave() {
        String reply = postProcessor.refine(
                "查询我的年假余额",
                "年假 5 天，病假 3 天。",
                Map.of("query_leave_balance", ToolResult.success("ok",
                        Map.of("annualLeaveBalance", 5, "sickLeaveBalance", 3)))
        );

        assertThat(reply).isEqualTo("您的年假余额为 5 天。");
        assertThat(reply).doesNotContain("病假");
    }

    @Test
    void shouldOnlyReturnSickBalanceWhenUserOnlyAsksSickLeave() {
        String reply = postProcessor.refine(
                "查询我的病假余额",
                "年假 5 天，病假 3 天。",
                Map.of("query_leave_balance", ToolResult.success("ok",
                        Map.of("annualLeaveBalance", 5, "sickLeaveBalance", 3)))
        );

        assertThat(reply).isEqualTo("您的病假余额为 3 天。");
        assertThat(reply).doesNotContain("年假");
    }

    @Test
    void shouldKeepModelReplyWhenUserAsksAllLeaveBalances() {
        String modelReply = "您的年假余额为 5 天，病假余额为 3 天。";

        String reply = postProcessor.refine(
                "查询我的年假和病假余额",
                modelReply,
                Map.of("query_leave_balance", ToolResult.success("ok",
                        Map.of("annualLeaveBalance", 5, "sickLeaveBalance", 3)))
        );

        assertThat(reply).isEqualTo(modelReply);
    }

    @Test
    void shouldKeepPendingReplyWhenLeavePendingWasCreated() {
        String pendingReply = "已为您创建一条待确认的请假申请，请回复“确认”或“取消”。";

        String reply = postProcessor.refine(
                "帮我请明天的年假",
                pendingReply,
                Map.of(
                        "query_leave_balance", ToolResult.success("ok",
                                Map.of("annualLeaveBalance", 5, "sickLeaveBalance", 3)),
                        "create_leave_pending", ToolResult.success("ok",
                                Map.of("leaveType", "ANNUAL",
                                        "startTime", "2026-06-29 09:00:00",
                                        "endTime", "2026-06-29 18:00:00"))
                )
        );

        assertThat(reply).isEqualTo(pendingReply);
    }
}
