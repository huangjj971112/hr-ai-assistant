package com.example.hrai.ai.observation;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentObservationBuilderTest {

    @Test
    void shouldKeepStepOrderTraceIdDurationAndCompletedSummaries() {
        AgentObservationBuilder builder = new AgentObservationBuilder();
        AgentObservationBuilder.AgentStepHandle policy = builder.startAgent("PolicyAgent", "开始查制度");
        builder.addToolCall(policy, tool("query_leave_policy", "trace-policy"));
        builder.finishAgent(policy, AgentObservationStatus.SUCCESS, "已查询请假制度");
        AgentObservationBuilder.AgentStepHandle attendance = builder.startAgent("AttendanceAgent", null);
        builder.addToolCall(attendance, tool("query_attendance", "trace-attendance"));
        builder.finishAgent(attendance, AgentObservationStatus.SUCCESS, "已核对考勤");

        AgentObservationSnapshot snapshot = builder.build();

        assertThat(snapshot.requestId()).isNotBlank();
        assertThat(snapshot.totalDurationMs()).isGreaterThanOrEqualTo(0);
        assertThat(snapshot.status()).isEqualTo(AgentObservationStatus.SUCCESS);
        assertThat(snapshot.summarySteps()).containsExactly("已查询请假制度", "已核对考勤");
        assertThat(snapshot.steps()).extracting(AgentObservationStep::agentName)
                .containsExactly("PolicyAgent", "AttendanceAgent");
        assertThat(snapshot.steps().get(0).durationMs()).isGreaterThanOrEqualTo(0);
        assertThat(snapshot.steps().get(0).toolCalls().get(0).traceId()).isEqualTo("trace-policy");
        assertThat(snapshot.steps().get(1).toolCalls().get(0).traceId()).isEqualTo("trace-attendance");
    }

    @Test
    void shouldIgnoreBlankCompletedSummary() {
        AgentObservationBuilder builder = new AgentObservationBuilder();
        AgentObservationBuilder.AgentStepHandle handle = builder.startAgent("LeaveAgent", "开始执行");

        builder.finishAgent(handle, AgentObservationStatus.SUCCESS, "  ");

        assertThat(builder.build().summarySteps()).isEmpty();
    }

    @Test
    void shouldDerivePartialAndFailedStatus() {
        AgentObservationBuilder partialBuilder = new AgentObservationBuilder();
        var success = partialBuilder.startAgent("PolicyAgent", null);
        partialBuilder.finishAgent(success, AgentObservationStatus.SUCCESS, "制度查询成功");
        var failed = partialBuilder.startAgent("AttendanceAgent", null);
        partialBuilder.finishAgent(failed, AgentObservationStatus.FAILED, "考勤查询失败");

        AgentObservationBuilder failedBuilder = new AgentObservationBuilder();
        var failedOne = failedBuilder.startAgent("PolicyAgent", null);
        failedBuilder.finishAgent(failedOne, AgentObservationStatus.FAILED, "制度查询失败");
        var failedTwo = failedBuilder.startAgent("AttendanceAgent", null);
        failedBuilder.finishAgent(failedTwo, AgentObservationStatus.FAILED, "考勤查询失败");

        AgentObservationBuilder explicitPartialBuilder = new AgentObservationBuilder();
        var partial = explicitPartialBuilder.startAgent("LeaveAgent", null);
        explicitPartialBuilder.finishAgent(partial, AgentObservationStatus.PARTIAL, "仅完成余额查询");

        assertThat(partialBuilder.build().status()).isEqualTo(AgentObservationStatus.PARTIAL);
        assertThat(failedBuilder.build().status()).isEqualTo(AgentObservationStatus.FAILED);
        assertThat(explicitPartialBuilder.build().status()).isEqualTo(AgentObservationStatus.PARTIAL);
        assertThat(new AgentObservationBuilder().build().status()).isEqualTo(AgentObservationStatus.SUCCESS);
    }

    @Test
    void shouldDerivePartialWhenSuccessfulAgentContainsFailedTool() {
        AgentObservationBuilder builder = new AgentObservationBuilder();
        var handle = builder.startAgent("PolicyAgent", null);
        builder.addToolCall(handle, tool("query_leave_policy", "trace-failed", AgentObservationStatus.FAILED));
        builder.finishAgent(handle, AgentObservationStatus.SUCCESS, "Agent 已完成但工具失败");

        assertThat(builder.build().status()).isEqualTo(AgentObservationStatus.PARTIAL);
    }

    @Test
    void shouldDeriveFailedWhenAllAgentAndToolStatusesFailed() {
        AgentObservationBuilder builder = new AgentObservationBuilder();
        var handle = builder.startAgent("PolicyAgent", null);
        builder.addToolCall(handle, tool("query_leave_policy", "trace-failed", AgentObservationStatus.FAILED));
        builder.finishAgent(handle, AgentObservationStatus.FAILED, "制度查询失败");

        assertThat(builder.build().status()).isEqualTo(AgentObservationStatus.FAILED);
    }

    @Test
    void shouldKeepDecisionAndBuiltSnapshotsDefensivelyImmutable() {
        AgentObservationBuilder builder = new AgentObservationBuilder();
        var handle = builder.startAgent("PolicyAgent", null);
        builder.addToolCall(handle, tool("query_leave_policy", "trace-1"));
        builder.finishAgent(handle, AgentObservationStatus.SUCCESS, "第一步完成");
        AgentDecisionObservation decision = new AgentDecisionObservation("ALLOW", "制度允许", true);
        AgentPlannerObservation planner = new AgentPlannerObservation(
                "LLM", "planner-trace", null, "需要查询制度"
        );
        AgentReflectionObservation reflection = new AgentReflectionObservation(
                "reflection-trace", "PASS", "PASS", "结果完整", false, false, ""
        );
        builder.planner(planner);
        builder.reflection(reflection);
        builder.decision(decision);

        AgentObservationSnapshot first = builder.build();
        var secondHandle = builder.startAgent("AttendanceAgent", null);
        builder.addToolCall(secondHandle, tool("query_attendance", "trace-2"));
        builder.finishAgent(secondHandle, AgentObservationStatus.SUCCESS, "第二步完成");
        AgentObservationSnapshot second = builder.build();

        assertThat(first.decision()).isEqualTo(decision);
        assertThat(first.planner()).isEqualTo(planner);
        assertThat(first.reflection()).isEqualTo(reflection);
        assertThat(first.steps()).hasSize(1);
        assertThat(first.steps().get(0).toolCalls()).hasSize(1);
        assertThat(first.summarySteps()).containsExactly("第一步完成");
        assertThat(second.steps()).hasSize(2);
        assertThat(second.steps().get(0).toolCalls()).hasSize(1);
        assertThat(second.steps().get(1).toolCalls()).hasSize(1);
        assertThatThrownBy(() -> first.steps().add(first.steps().get(0)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> first.steps().get(0).toolCalls().add(tool("other", "trace-3")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectRepeatedFinishAndToolCallsAfterFinish() {
        AgentObservationBuilder builder = new AgentObservationBuilder();
        var handle = builder.startAgent("PolicyAgent", "开始查制度");

        builder.finishAgent(handle, AgentObservationStatus.SUCCESS, "制度查询完成");

        assertThatThrownBy(() -> builder.finishAgent(handle, AgentObservationStatus.SUCCESS, "重复完成"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已完成");
        assertThatThrownBy(() -> builder.addToolCall(handle, tool("query_leave_policy", "trace-late")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已完成");
    }

    @Test
    void shouldRejectNullStatusAndNullToolObservation() {
        AgentObservationBuilder builder = new AgentObservationBuilder();
        var handle = builder.startAgent("PolicyAgent", null);

        assertThatThrownBy(() -> builder.finishAgent(handle, null, "状态缺失"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> builder.addToolCall(handle, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AgentToolObservation(
                "query_leave_policy", null, 1, "trace", Map.of(), Map.of(), null, null
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectExternalHandle() {
        AgentObservationBuilder builder = new AgentObservationBuilder();
        var externalHandle = new AgentObservationBuilder.AgentStepHandle("fake-id");

        assertThatThrownBy(() -> builder.finishAgent(externalHandle, AgentObservationStatus.SUCCESS, "伪造完成"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未知");
    }

    @Test
    void shouldDeepCopyToolSummariesWhenBuildingSnapshots() {
        AgentObservationBuilder builder = new AgentObservationBuilder();
        var handle = builder.startAgent("PolicyAgent", null);
        Map<String, Object> nested = new HashMap<>();
        nested.put("secret", "before");
        Map<String, Object> inputSummary = new HashMap<>();
        inputSummary.put("nested", nested);
        inputSummary.put("visible", "safe");
        builder.addToolCall(handle, new AgentToolObservation(
                "query_leave_policy",
                AgentObservationStatus.SUCCESS,
                1,
                "trace",
                inputSummary,
                Map.of("items", List.of("before")),
                null,
                null
        ));
        builder.finishAgent(handle, AgentObservationStatus.SUCCESS, "完成");

        AgentObservationSnapshot snapshot = builder.build();
        nested.put("secret", "after");

        AgentToolObservation tool = snapshot.steps().get(0).toolCalls().get(0);
        assertThat(tool.inputSummary()).containsEntry("visible", "safe");
        assertThat(tool.inputSummary()).doesNotContainKey("nested");
        assertThat(tool.resultSummary()).doesNotContainKey("items");
        assertThat(tool.toString()).doesNotContain("before", "after");
    }

    private AgentToolObservation tool(String toolName, String traceId) {
        return tool(toolName, traceId, AgentObservationStatus.SUCCESS);
    }

    private AgentToolObservation tool(
            String toolName,
            String traceId,
            AgentObservationStatus status
    ) {
        return new AgentToolObservation(
                toolName,
                status,
                1,
                traceId,
                Map.of(),
                Map.of(),
                null,
                null
        );
    }
}
