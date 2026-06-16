package com.example.hrai.ai.multiagent;

import com.example.hrai.dto.ai.AiChatResponse;
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

    private DefaultCoordinatorAgent coordinatorAgent;

    @BeforeEach
    void setUp() {
        coordinatorAgent = new DefaultCoordinatorAgent(new AgentDispatchPlanner(), policyAgent, attendanceAgent,
                leaveAgent, salaryAgent, Clock.fixed(Instant.parse("2026-06-16T04:00:00Z"), ZoneId.of("Asia/Shanghai")));
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
        verify(leaveAgent, never()).evaluate(any(), any());
    }
}
