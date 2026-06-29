package com.example.hrai.ai.multiagent;

import com.example.hrai.dto.ai.AiChatResponse;
import com.example.hrai.entity.UserRole;
import com.example.hrai.ai.reflection.ReflectionContext;
import com.example.hrai.ai.reflection.ReflectionResult;
import com.example.hrai.ai.reflection.ReflectionService;
import com.example.hrai.security.AuthenticatedUser;
import com.example.hrai.security.CurrentUserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

@ExtendWith(MockitoExtension.class)
class DefaultCoordinatorAgentTest {

    @Mock
    private PolicyAgent policyAgent;

    @Mock
    private AttendanceAgent attendanceAgent;

    @Mock
    private LeaveAgent leaveAgent;

    @Mock
    private SalaryAgent salaryAgent;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private ReflectionService reflectionService;

    private DefaultCoordinatorAgent coordinatorAgent;

    @BeforeEach
    void setUp() {
        LlmAgentDispatchPlanner planner = new LlmAgentDispatchPlanner(
                new AgentDispatchPlanner(), new ObjectMapper(), prompt -> {
                    throw new IllegalStateException("force rule fallback in coordinator tests");
                }
        );
        when(currentUserService.currentUser()).thenReturn(new AuthenticatedUser(
                1L, "zhangsan", "张三", UserRole.EMPLOYEE
        ));
        when(reflectionService.reflect(any(ReflectionContext.class)))
                .thenReturn(ReflectionResult.pass("测试放行"));
        coordinatorAgent = new DefaultCoordinatorAgent(planner, currentUserService, policyAgent, attendanceAgent,
                leaveAgent, salaryAgent, reflectionService,
                Clock.fixed(Instant.parse("2026-06-16T04:00:00Z"), ZoneId.of("Asia/Shanghai")));
    }

    @Test
    void shouldCoordinatePolicyLeaveAndSalaryForAnnualLeaveSalaryQuestion() {
        PolicyAgentResult policy = new PolicyAgentResult(true, "年假属于带薪假期，正常审批不扣工资。", List.of("制度库"), "p1");
        LeaveAgentResult leave = new LeaveAgentResult(true, "年假余额 5 天", null, null, "l1");
        SalaryImpactResult salary = new SalaryImpactResult(SalaryImpactLevel.NO_IMPACT,
                "通常不影响工资", "年假属于带薪假期", false);
        when(policyAgent.query(any())).thenReturn(policy);
        when(leaveAgent.evaluate(any(), any())).thenReturn(leave);
        when(salaryAgent.evaluate(any())).thenReturn(salary);

        AiChatResponse response = coordinatorAgent.chat("我下周想请三天年假，会不会影响工资？", "session-1");

        assertThat(response.getIntent()).isEqualTo("MULTI_AGENT_RESPONSE");
        assertThat(response.getReply()).contains("PolicyAgent", "LeaveAgent", "SalaryAgent", "通常不影响工资");
        assertThat(response.getData()).isInstanceOf(MultiAgentResult.class);
        assertThat(response.getObservation()).isNotNull();
        assertThat(response.getObservation().steps()).extracting("agentName")
                .containsExactly("PolicyAgent", "LeaveAgent", "SalaryAgent");
        assertThat(response.getObservation().steps().get(0).toolCalls().get(0).toolName())
                .isEqualTo("query_leave_policy");
        assertThat(response.getObservation().steps().get(1).toolCalls().get(0).toolName())
                .isEqualTo("query_leave_balance");
        assertThat(response.getObservation().decision().outcome()).isEqualTo("NO_IMPACT");
        assertThat(response.getObservation().decision().needsHumanConfirmation()).isFalse();
        verify(attendanceAgent, never()).query(any(), any());
    }

    @Test
    void shouldCoordinateAttendancePolicyAndSalaryForLateSalaryQuestion() {
        PolicyAgentResult policy = new PolicyAgentResult(true, "迟到可能影响绩效或扣款。", List.of("考勤制度"), "p1");
        AttendanceAgentResult attendance = new AttendanceAgentResult(true, List.of(), 2, "a1");
        SalaryImpactResult salary = new SalaryImpactResult(SalaryImpactLevel.POSSIBLE_IMPACT,
                "可能影响工资", "迟到可能影响绩效或扣款。", true);
        when(policyAgent.query(any())).thenReturn(policy);
        when(attendanceAgent.query(any(), any())).thenReturn(attendance);
        when(salaryAgent.evaluate(any())).thenReturn(salary);

        AiChatResponse response = coordinatorAgent.chat("我这周迟到了两次，会影响工资吗？", "session-1");

        assertThat(response.getReply()).contains("AttendanceAgent", "PolicyAgent", "SalaryAgent", "可能影响工资");
        assertThat(response.getObservation()).isNotNull();
        assertThat(response.getObservation().steps()).extracting("agentName")
                .containsExactly("PolicyAgent", "AttendanceAgent", "SalaryAgent");
        assertThat(response.getObservation().steps().get(1).toolCalls().get(0).resultSummary())
                .containsEntry("lateCount", 2);
        assertThat(response.getObservation().decision().needsHumanConfirmation()).isTrue();
        verify(leaveAgent, never()).evaluate(any(), any());
    }

    @Test
    void shouldShowFailureSummaryWhenPolicyAgentFails() {
        PolicyAgentResult policy = new PolicyAgentResult(false, "", List.of(), "p-failed");
        SalaryImpactResult salary = new SalaryImpactResult(SalaryImpactLevel.UNKNOWN,
                "当前制度依据不足", "未获得明确制度依据", true);
        when(policyAgent.query(any())).thenReturn(policy);
        when(salaryAgent.evaluate(any())).thenReturn(salary);

        AiChatResponse response = coordinatorAgent.chat("我想请病假，会不会扣工资？", "session-1");

        assertThat(response.getObservation()).isNotNull();
        assertThat(response.getObservation().steps().get(0).status())
                .isEqualTo(com.example.hrai.ai.observation.AgentObservationStatus.FAILED);
        assertThat(response.getObservation().steps().get(0).summary())
                .isEqualTo("PolicyAgent 制度查询失败");
        assertThat(response.getObservation().steps().get(0).toolCalls().get(0).errorCode())
                .isEqualTo("MCP_TOOL_CALL_FAILED");
    }

    @Test
    void shouldMeasureMultiAgentToolDuration() {
        PolicyAgentResult policy = new PolicyAgentResult(true, "病假工资可能按比例发放。", List.of("制度库"), "p1");
        SalaryImpactResult salary = new SalaryImpactResult(SalaryImpactLevel.POSSIBLE_IMPACT,
                "可能影响工资", "病假工资可能按比例发放。", true);
        doAnswer(invocation -> {
            Thread.sleep(5);
            return policy;
        }).when(policyAgent).query(any());
        when(salaryAgent.evaluate(any())).thenReturn(salary);

        AiChatResponse response = coordinatorAgent.chat("我想请病假，会不会扣工资？", "session-1");

        assertThat(response.getObservation().steps().get(0).toolCalls().get(0).durationMs())
                .isGreaterThan(0);
    }
}
