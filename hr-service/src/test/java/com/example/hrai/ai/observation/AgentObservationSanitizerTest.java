package com.example.hrai.ai.observation;

import com.example.hrai.ai.mcp.dto.PendingLeaveMcpResult;
import com.example.hrai.ai.tool.ToolResult;
import com.example.hrai.dto.attendance.AttendanceRecordResponse;
import com.example.hrai.dto.knowledge.KnowledgeAskResponse;
import com.example.hrai.dto.leave.LeaveApplyResponse;
import com.example.hrai.dto.leave.LeaveBalanceResponse;
import com.example.hrai.entity.LeaveType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalField;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class AgentObservationSanitizerTest {

    private final AgentObservationSanitizer sanitizer = new AgentObservationSanitizer();

    @Test
    void shouldWhitelistInputAndHideCredentialsAndPrivateMessage() {
        Map<String, Object> sanitized = sanitizer.sanitizeInput("create_leave_pending", Map.of(
                "toolToken", "secret-token",
                "sessionId", "session-secret",
                "idempotencyKey", "idempotency-secret",
                "message", "我因身体不适需要请假，具体病情属于隐私",
                "startDate", "2026-06-26",
                "endDate", "2026-06-27",
                "expectedVersion", 3,
                "pendingId", "pending-1234567890"
        ));

        assertThat(sanitized)
                .containsEntry("startDate", "2026-06-26")
                .containsEntry("endDate", "2026-06-27")
                .containsEntry("request", "使用用户原始请假描述，原因已隐藏");
        assertThat(sanitized.toString())
                .doesNotContain("secret-token", "session-secret", "idempotency-secret")
                .doesNotContain("身体不适", "具体病情")
                .doesNotContain("pending-1234567890");
    }

    @Test
    void shouldTruncatePolicyQuestionAndIgnoreIrrelevantDates() {
        String question = "年假是否可以结转以及跨年度使用，有哪些审批要求和适用范围？".repeat(4);

        Map<String, Object> sanitized = sanitizer.sanitizeInput("query_leave_policy", Map.of(
                "question", question,
                "startDate", LocalDate.of(2026, 6, 1),
                "endDate", LocalDate.of(2026, 6, 30)
        ));

        assertThat((String) sanitized.get("question")).hasSize(80);
        assertThat(sanitized).containsOnlyKeys("question");
    }

    @Test
    void shouldIgnoreComplexOrWrongTypedInputValues() {
        Map<String, Object> sensitive = Map.of("toolToken", "secret-token", "reason", "完整隐私原因");

        Map<String, Object> sanitized = sanitizer.sanitizeInput("query_leave_policy", Map.of(
                "startDate", sensitive,
                "endDate", List.of("session-secret"),
                "expectedVersion", new SensitiveValue("custom-secret"),
                "question", sensitive,
                "pendingId", sensitive
        ));
        Map<String, Object> pendingInput = sanitizer.sanitizeInput(
                "confirm_leave_apply", Map.of("pendingId", sensitive)
        );

        assertThat(sanitized).isEmpty();
        assertThat(pendingInput).isEmpty();
        assertThat(sanitized.toString() + pendingInput)
                .doesNotContain("secret-token", "session-secret", "完整隐私原因", "custom-secret");
    }

    @Test
    void shouldUseToolSpecificInputWhitelistAndStrictDateFormat() {
        Map<String, Object> unknown = sanitizer.sanitizeInput("unknown_tool", Map.of(
                "startDate", "2026-06-26",
                "endDate", "2026-06-27",
                "expectedVersion", 3,
                "question", "不该展示的问题"
        ));
        Map<String, Object> balance = sanitizer.sanitizeInput("query_leave_balance", Map.of(
                "startDate", "2026-06-26",
                "endDate", "2026-06-27",
                "expectedVersion", 3
        ));
        Map<String, Object> attendance = sanitizer.sanitizeInput("query_attendance", Map.of(
                "startDate", "secret-token-and-private-reason",
                "endDate", "2026/06/27",
                "expectedVersion", 3
        ));

        assertThat(unknown).isEmpty();
        assertThat(balance).isEmpty();
        assertThat(attendance).isEmpty();
        assertThat(unknown.toString() + balance + attendance)
                .doesNotContain("secret-token", "private-reason", "不该展示的问题");
    }

    @Test
    void shouldSummarizeConfirmAndCancelInputOnlyWithPendingIdAndVersion() {
        Map<String, Object> confirm = sanitizer.sanitizeInput("confirm_leave_apply", Map.of(
                "pendingId", "pending-1234567890",
                "expectedVersion", 7,
                "startDate", "2026-06-26",
                "message", "不应展示的确认说明"
        ));
        Map<String, Object> cancel = sanitizer.sanitizeInput("cancel_pending", Map.of(
                "pendingId", "pending-abcdefghi",
                "expectedVersion", 8,
                "reason", "不应展示的取消原因"
        ));

        assertThat(confirm).containsEntry("expectedVersion", 7)
                .containsKey("pendingId")
                .doesNotContainKeys("startDate", "message");
        assertThat(cancel).containsEntry("expectedVersion", 8)
                .containsKey("pendingId")
                .doesNotContainKey("reason");
        assertThat(confirm.toString() + cancel)
                .doesNotContain("pending-1234567890", "pending-abcdefghi")
                .doesNotContain("不应展示");
    }

    @Test
    void shouldNotTreatSuccessAsSafeFailureCode() {
        ToolResult<Void> failure = ToolResult.failure(
                "trace-success",
                "SUCCESS",
                "secret-token should not leak"
        );

        Map<String, Object> summary = sanitizer.sanitizeResult("query_leave_balance", failure);

        assertThat(summary).containsEntry("errorCode", "MCP_TOOL_CALL_FAILED");
        assertThat(summary.toString()).doesNotContain("secret-token", "SUCCESS");
    }

    @Test
    void shouldSummarizeBalanceAttendanceAndPolicyDtoResults() {
        Map<String, Object> balance = sanitizer.sanitizeResult("query_leave_balance",
                ToolResult.success("trace-balance", "ok", new LeaveBalanceResponse("张三", 5, 8)));
        List<AttendanceRecordResponse> records = List.of(
                attendance("LATE"),
                attendance("NORMAL"),
                attendance("LATE")
        );
        Map<String, Object> attendance = sanitizer.sanitizeResult("query_attendance",
                ToolResult.success("trace-attendance", "ok", records));
        String answer = "制度摘要".repeat(50);
        ToolResult<KnowledgeAskResponse> policyResult = ToolResult.success(
                "trace-policy", "ok", new KnowledgeAskResponse(answer, "员工手册第八章", "private-conversation")
        );
        Map<String, Object> policy = sanitizer.sanitizeResult("query_leave_policy", policyResult);

        assertThat(balance).containsOnly(
                entry("annualLeaveBalance", 5),
                entry("sickLeaveBalance", 8)
        );
        assertThat(attendance).containsOnly(
                entry("recordCount", 3),
                entry("lateCount", 2L)
        );
        assertThat((String) policy.get("policySummary")).hasSize(120);
        assertThat(policy).containsEntry("source", "员工手册第八章");
        assertThat(policy.toString()).doesNotContain("private-conversation");
        assertThat(sanitizer.evidenceSource("query_leave_policy", policyResult)).isEqualTo("员工手册第八章");
        assertThat(sanitizer.evidenceSource("query_leave_balance",
                ToolResult.success("ok", Map.of("source", "不应输出")))).isNull();
    }

    @Test
    void shouldSummarizeMapResultsForAllReadTools() {
        Map<String, Object> balance = sanitizer.sanitizeResult("query_leave_balance",
                ToolResult.success("ok", Map.of("annualLeaveBalance", 4, "sickLeaveBalance", 7, "employeeName", "张三")));
        Map<String, Object> attendance = sanitizer.sanitizeResult("query_attendance",
                ToolResult.success("ok", List.of(Map.of("status", "LATE"), Map.of("status", "NORMAL"))));
        Map<String, Object> policy = sanitizer.sanitizeResult("query_leave_policy",
                ToolResult.success("ok", Map.of("answer", "病假按制度执行", "source", "员工手册", "raw", "secret")));

        assertThat(balance).containsOnlyKeys("annualLeaveBalance", "sickLeaveBalance");
        assertThat(attendance).containsOnly(
                entry("recordCount", 2),
                entry("lateCount", 1L)
        );
        assertThat(policy).containsOnly(
                entry("policySummary", "病假按制度执行"),
                entry("source", "员工手册")
        );
    }

    @Test
    void shouldMaskPendingIdAndNeverExposeReasonForDtoAndMap() {
        PendingLeaveMcpResult dto = new PendingLeaveMcpResult(
                "PENDING", "张三", LeaveType.ANNUAL,
                "2026-06-26 09:00:00", "2026-06-26 18:00:00",
                "陪家人就医，包含隐私原因", false,
                "2026-06-25 10:00:00", "2026-06-25 10:30:00",
                "pending-1234567890", 1, "WAITING_CONFIRM"
        );
        Map<String, Object> dtoSummary = sanitizer.sanitizeResult(
                "create_leave_pending", ToolResult.success("ok", dto)
        );
        Map<String, Object> mapSummary = sanitizer.sanitizeResult(
                "cancel_pending", ToolResult.success("ok", Map.of(
                        "leaveType", "SICK",
                        "startTime", "2026-06-27 09:00:00",
                        "endTime", "2026-06-27 18:00:00",
                        "status", "CANCELLED",
                        "pendingId", "pending-abcdefghijk",
                        "reason", "详细病情"
                ))
        );

        assertThat(dtoSummary).containsKeys("leaveType", "startTime", "endTime", "status", "pendingId");
        assertThat(dtoSummary.toString())
                .doesNotContain("pending-1234567890")
                .doesNotContain("陪家人就医", "隐私原因");
        assertThat(mapSummary.toString())
                .doesNotContain("pending-abcdefghijk")
                .doesNotContain("详细病情");
    }

    @Test
    void shouldSummarizeConfirmAndFailureWithoutCopyingData() {
        Map<String, Object> dtoConfirm = sanitizer.sanitizeResult("confirm_leave_apply",
                ToolResult.success("ok", new LeaveApplyResponse("LEAVE-001", "PENDING", "内部消息")));
        Map<String, Object> mapConfirm = sanitizer.sanitizeResult("confirm_leave_apply",
                ToolResult.success("ok", Map.of("applyNo", "LEAVE-002", "status", "APPROVED", "reason", "隐私")));
        ToolResult<Map<String, Object>> failure = new ToolResult<>(
                false, "PENDING_VERSION_CONFLICT",
                "toolToken=secret-token sessionId=session-secret 原因=完整隐私原因",
                "trace-private",
                Map.of("reason", "绝不能复制的数据")
        );
        Map<String, Object> failureSummary = sanitizer.sanitizeResult("create_leave_pending", failure);
        ToolResult<Void> unsafeCodeFailure = ToolResult.failure(
                "trace-unsafe", "BAD CODE: secret-token", "sessionId=session-secret"
        );
        Map<String, Object> unsafeCodeSummary = sanitizer.sanitizeResult(
                "query_leave_balance", unsafeCodeFailure
        );
        Map<String, Object> unknownSafeFormatCodeSummary = sanitizer.sanitizeResult(
                "query_leave_balance",
                ToolResult.failure("trace-secret", "secret-token", "不应输出的上游消息")
        );

        assertThat(dtoConfirm).containsOnly(entry("applyNo", "LEAVE-001"), entry("status", "PENDING"));
        assertThat(mapConfirm).containsOnly(entry("applyNo", "LEAVE-002"), entry("status", "APPROVED"));
        assertThat(failureSummary).containsOnly(
                entry("errorCode", "PENDING_VERSION_CONFLICT"),
                entry("errorMessage", "工具调用失败，请查看错误码")
        );
        assertThat(unsafeCodeSummary).containsEntry("errorCode", "MCP_TOOL_CALL_FAILED");
        assertThat(unknownSafeFormatCodeSummary).containsEntry("errorCode", "MCP_TOOL_CALL_FAILED");
        assertThat(failureSummary.toString() + unsafeCodeSummary + unknownSafeFormatCodeSummary)
                .doesNotContain("secret-token", "session-secret", "完整隐私原因")
                .doesNotContain("绝不能复制的数据", "trace-private", "BAD CODE");
    }

    @Test
    void shouldRejectCustomNumberAndTemporalAccessorWithoutCallingSensitiveToString() {
        SensitiveNumber sensitiveNumber = new SensitiveNumber("number-secret");
        SensitiveTemporal sensitiveTemporal = new SensitiveTemporal("temporal-secret");

        Map<String, Object> input = sanitizer.sanitizeInput("query_attendance", Map.of(
                "expectedVersion", sensitiveNumber,
                "startDate", sensitiveTemporal,
                "endDate", sensitiveTemporal
        ));
        Map<String, Object> balance = sanitizer.sanitizeResult("query_leave_balance",
                ToolResult.success("ok", Map.of(
                        "annualLeaveBalance", sensitiveNumber,
                        "sickLeaveBalance", sensitiveNumber
                )));
        Map<String, Object> pending = sanitizer.sanitizeResult("create_leave_pending",
                ToolResult.success("ok", Map.of(
                        "startTime", sensitiveTemporal,
                        "endTime", sensitiveTemporal
                )));

        assertThat(input).isEmpty();
        assertThat(balance).isEmpty();
        assertThat(pending).isEmpty();
        assertThat(input.toString() + balance + pending)
                .doesNotContain("number-secret", "temporal-secret");
    }

    @Test
    void shouldIgnoreComplexValuesInResultWhitelists() {
        Map<String, Object> sensitive = Map.of("toolToken", "secret-token", "reason", "完整隐私原因");

        Map<String, Object> balance = sanitizer.sanitizeResult("query_leave_balance",
                ToolResult.success("ok", Map.of(
                        "annualLeaveBalance", sensitive,
                        "sickLeaveBalance", List.of("session-secret")
                )));
        Map<String, Object> policy = sanitizer.sanitizeResult("query_leave_policy",
                ToolResult.success("ok", Map.of("answer", sensitive, "source", sensitive)));
        Map<String, Object> pending = sanitizer.sanitizeResult("create_leave_pending",
                ToolResult.success("ok", Map.of(
                        "leaveType", sensitive,
                        "startTime", sensitive,
                        "endTime", new SensitiveValue("custom-secret"),
                        "status", sensitive,
                        "pendingId", sensitive
                )));
        Map<String, Object> confirm = sanitizer.sanitizeResult("confirm_leave_apply",
                ToolResult.success("ok", Map.of("applyNo", sensitive, "status", sensitive)));

        assertThat(balance).isEmpty();
        assertThat(policy).isEmpty();
        assertThat(pending).isEmpty();
        assertThat(confirm).isEmpty();
        assertThat(balance.toString() + policy + pending + confirm)
                .doesNotContain("secret-token", "session-secret", "完整隐私原因", "custom-secret");
    }

    private AttendanceRecordResponse attendance(String status) {
        return new AttendanceRecordResponse(
                "张三", LocalDate.of(2026, 6, 25),
                LocalTime.of(9, 10), LocalTime.of(18, 0), status, "private remark"
        );
    }

    private record SensitiveValue(String secret) {
    }

    private static final class SensitiveNumber extends Number {

        private final String secret;

        private SensitiveNumber(String secret) {
            this.secret = secret;
        }

        @Override
        public int intValue() {
            return 0;
        }

        @Override
        public long longValue() {
            return 0;
        }

        @Override
        public float floatValue() {
            return 0;
        }

        @Override
        public double doubleValue() {
            return 0;
        }

        @Override
        public String toString() {
            return secret;
        }
    }

    private static final class SensitiveTemporal implements TemporalAccessor {

        private final String secret;

        private SensitiveTemporal(String secret) {
            this.secret = secret;
        }

        @Override
        public boolean isSupported(TemporalField field) {
            return false;
        }

        @Override
        public long getLong(TemporalField field) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString() {
            return secret;
        }
    }
}
