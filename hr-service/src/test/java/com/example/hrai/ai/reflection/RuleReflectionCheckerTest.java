package com.example.hrai.ai.reflection;

import com.example.hrai.ai.observation.AgentObservationSnapshot;
import com.example.hrai.ai.observation.AgentObservationStatus;
import com.example.hrai.ai.observation.AgentObservationStep;
import com.example.hrai.ai.observation.AgentToolObservation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuleReflectionCheckerTest {

    private final RuleReflectionChecker checker = new RuleReflectionChecker();

    @Test
    void shouldFailWhenToolReturnsForbidden() {
        ReflectionResult result = checker.check(context(tool("query_leave_balance",
                AgentObservationStatus.FAILED,
                Map.of(),
                Map.of(),
                "FORBIDDEN")));

        assertThat(result.action()).isEqualTo(ReflectionAction.FAIL);
        assertThat(result.userMessage()).contains("没有权限");
    }

    @Test
    void shouldAskUserWhenAttendanceReturnsEmptyData() {
        ReflectionResult result = checker.check(context(tool("query_attendance",
                AgentObservationStatus.SUCCESS,
                Map.of("startDate", "2026-06-01", "endDate", "2026-06-30"),
                Map.of("recordCount", 0),
                null)));

        assertThat(result.action()).isEqualTo(ReflectionAction.ASK_USER);
        assertThat(result.userMessage()).contains("补充");
    }

    @Test
    void shouldPassEmptyAttendanceWhenFinalAnswerAlreadyHasSalaryConclusion() {
        ReflectionResult result = checker.check(context(
                tool("query_attendance",
                        AgentObservationStatus.SUCCESS,
                        Map.of("startDate", "2026-06-22", "endDate", "2026-06-29"),
                        Map.of("recordCount", 0, "lateCount", 0),
                        null),
                "根据制度，迟到可能影响工资或绩效，建议以 HR 审核为准。"
        ));

        assertThat(result.action()).isEqualTo(ReflectionAction.PASS);
    }

    private ReflectionContext context(AgentToolObservation tool) {
        return context(tool, "");
    }

    private ReflectionContext context(AgentToolObservation tool, String finalAnswer) {
        AgentObservationStep step = new AgentObservationStep(
                "SpringAiMcpModelAgent",
                AgentObservationStatus.SUCCESS,
                1,
                "测试步骤",
                List.of(tool)
        );
        AgentObservationSnapshot snapshot = new AgentObservationSnapshot(
                "request-1",
                AgentObservationStatus.SUCCESS,
                1,
                List.of("测试步骤"),
                List.of(step),
                null
        );
        return new ReflectionContext("用户问题", null, snapshot, finalAnswer, null);
    }

    private AgentToolObservation tool(
            String toolName,
            AgentObservationStatus status,
            Map<String, Object> input,
            Map<String, Object> result,
            String errorCode
    ) {
        return new AgentToolObservation(toolName, status, 1, "trace-1", input, result, null, errorCode);
    }
}
