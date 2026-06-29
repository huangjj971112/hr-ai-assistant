package com.example.hrai.ai.reflection;

import com.example.hrai.ai.observation.AgentObservationSnapshot;
import com.example.hrai.ai.observation.AgentObservationStatus;
import com.example.hrai.ai.observation.AgentObservationStep;
import com.example.hrai.ai.observation.AgentToolObservation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReflectionServiceTest {

    @Mock
    private RuleReflectionChecker ruleChecker;

    @Mock
    private LlmReflectionChecker llmChecker;

    @Test
    void shouldDowngradeUserReportedAttendanceMismatchToPassWhenFinalAnswerIsUsable() {
        ReflectionContext context = context(
                "我这周迟到了两次，会影响工资吗？",
                "根据制度，迟到可能影响工资或绩效；但系统本周考勤记录未查询到迟到，请以 HR 审核为准。"
        );
        ReflectionResult llmFail = ReflectionResult.fail(
                "执行结果存在矛盾：用户提到本周迟到两次，但AttendanceAgent查询结果显示迟到次数为0。",
                "请确认您的考勤情况，或提供具体日期以便重新查询。"
        );
        when(ruleChecker.check(context)).thenReturn(ReflectionResult.pass("规则放行"));
        when(llmChecker.check(context)).thenReturn(new LlmReflectionChecker.LlmReflectionResult(llmFail, """
                {
                  "action": "FAIL",
                  "reason": "执行结果存在矛盾：用户提到本周迟到两次，但AttendanceAgent查询结果显示迟到次数为0。",
                  "userMessage": "请确认您的考勤情况，或提供具体日期以便重新查询。",
                  "needRetry": true,
                  "needReplan": false
                }
                """));

        ReflectionResult result = new ReflectionService(ruleChecker, llmChecker).reflect(context);

        assertThat(result.action()).isEqualTo(ReflectionAction.PASS);
        assertThat(result.reason()).contains("用户自述与系统记录不一致");
        assertThat(result.llmRawOutput()).contains("\"action\": \"FAIL\"");
    }

    private ReflectionContext context(String originalMessage, String finalAnswer) {
        AgentToolObservation tool = new AgentToolObservation(
                "query_attendance",
                AgentObservationStatus.SUCCESS,
                1,
                "trace-attendance",
                Map.of("startDate", "2026-06-22", "endDate", "2026-06-29"),
                Map.of("recordCount", 0, "lateCount", 0),
                null,
                null
        );
        AgentObservationStep step = new AgentObservationStep(
                "AttendanceAgent",
                AgentObservationStatus.SUCCESS,
                1,
                "AttendanceAgent 已返回考勤事实",
                List.of(tool)
        );
        AgentObservationSnapshot snapshot = new AgentObservationSnapshot(
                "request-1",
                AgentObservationStatus.SUCCESS,
                1,
                List.of("AttendanceAgent 已返回考勤事实"),
                List.of(step),
                null,
                null,
                null
        );
        return new ReflectionContext(originalMessage, null, snapshot, finalAnswer, null);
    }
}
