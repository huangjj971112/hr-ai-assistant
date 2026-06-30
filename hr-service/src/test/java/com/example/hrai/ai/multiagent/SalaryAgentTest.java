package com.example.hrai.ai.multiagent;

import com.example.hrai.ai.mcp.client.HrMcpCaller;
import com.example.hrai.ai.security.ToolTokenService;
import com.example.hrai.ai.tool.ToolResult;
import com.example.hrai.entity.UserRole;
import com.example.hrai.security.AuthenticatedUser;
import com.example.hrai.security.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalaryAgentTest {

    @Mock
    private HrMcpCaller hrMcpCaller;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private ToolTokenService toolTokenService;

    private SalaryAgent salaryAgent;

    @BeforeEach
    void setUp() {
        salaryAgent = new SalaryAgent(
                hrMcpCaller,
                currentUserService,
                toolTokenService,
                Clock.fixed(Instant.parse("2026-06-30T04:00:00Z"), ZoneId.of("Asia/Shanghai"))
        );
    }

    @Test
    void shouldReturnNoImpactWhenPolicySaysAnnualLeaveIsPaid() {
        SalaryImpactResult result = salaryAgent.evaluate(new SalaryAgentInput(
                "我下周想请三天年假，会不会影响工资？",
                new PolicyAgentResult(true, "年假属于带薪假期，正常审批通过后不扣工资。", List.of("员工手册"), "trace-policy"),
                null,
                null
        ));

        assertThat(result.impactLevel()).isEqualTo(SalaryImpactLevel.NO_IMPACT);
        assertThat(result.summary()).contains("通常不影响工资");
        assertThat(result.basis()).contains("年假属于带薪假期");
        verify(hrMcpCaller, never()).call(anyString(), any());
    }

    @Test
    void shouldReturnPossibleImpactWhenPolicySaysLateMayDeductSalary() {
        SalaryImpactResult result = salaryAgent.evaluate(new SalaryAgentInput(
                "我这周迟到了两次，会影响工资吗？",
                new PolicyAgentResult(true, "迟到可能按照考勤制度影响绩效或扣款，具体以考勤规则为准。", List.of("考勤制度"), "trace-policy"),
                new AttendanceAgentResult(true, List.of(), 2, "trace-attendance"),
                null
        ));

        assertThat(result.impactLevel()).isEqualTo(SalaryImpactLevel.POSSIBLE_IMPACT);
        assertThat(result.summary()).contains("可能影响工资");
        assertThat(result.needsHumanConfirmation()).isTrue();
    }

    @Test
    void shouldReturnPossibleImpactWhenSickLeavePolicyMentionsSalaryRule() {
        SalaryImpactResult result = salaryAgent.evaluate(new SalaryAgentInput(
                "我想请病假，会不会扣工资？",
                new PolicyAgentResult(true, "病假工资按制度执行，可能按比例发放并存在扣款。", List.of("员工手册"), "trace-policy"),
                null,
                null
        ));

        assertThat(result.impactLevel()).isEqualTo(SalaryImpactLevel.POSSIBLE_IMPACT);
        assertThat(result.summary()).contains("病假可能按规则影响工资");
        assertThat(result.needsHumanConfirmation()).isTrue();
    }

    @Test
    void shouldReturnUnknownWhenPolicyIsNotExplicit() {
        SalaryImpactResult result = salaryAgent.evaluate(new SalaryAgentInput(
                "我想请病假，会不会扣工资？",
                new PolicyAgentResult(true, "制度中未明确说明薪资影响。", List.of("制度库"), "trace-policy"),
                null,
                null
        ));

        assertThat(result.impactLevel()).isEqualTo(SalaryImpactLevel.UNKNOWN);
        assertThat(result.summary()).contains("当前制度依据不足");
    }

    @Test
    void shouldQuerySalaryDetailForPayrollMismatchQuestion() {
        when(currentUserService.currentUser())
                .thenReturn(new AuthenticatedUser(1L, "zhangsan", "张三", UserRole.EMPLOYEE));
        when(toolTokenService.createToken(any(), anyString(), eq(Set.of("salary:detail:read"))))
                .thenReturn("tool-token");
        when(hrMcpCaller.call("query_salary", Map.of(
                "toolToken", "tool-token",
                "salaryMonth", "2026-06"
        ))).thenReturn(ToolResult.success("trace-salary", "ok", Map.of(
                "employeeName", "张三",
                "salaryMonth", "2026-06",
                "grossSalary", new BigDecimal("4000.00"),
                "netSalary", new BigDecimal("3000.00"),
                "attendanceDeduction", new BigDecimal("500.00"),
                "socialSecurity", new BigDecimal("300.00"),
                "housingFund", new BigDecimal("200.00"),
                "bonus", BigDecimal.ZERO,
                "totalDeduction", new BigDecimal("1000.00"),
                "remark", "本月实发较应发少 1000，主要来自考勤扣款、社保、公积金"
        )));

        SalaryImpactResult result = salaryAgent.evaluate(new SalaryAgentInput(
                "我这个月只发了三千，实际应发应该是四千",
                null,
                null,
                null
        ));

        assertThat(result.impactLevel()).isEqualTo(SalaryImpactLevel.POSSIBLE_IMPACT);
        assertThat(result.summary()).contains("应发工资 4000.00 元", "实发工资 3000.00 元", "差额 1000.00 元");
        assertThat(result.basis()).contains("考勤扣款 500.00 元", "社保 300.00 元", "公积金 200.00 元");
        assertThat(result.needsHumanConfirmation()).isTrue();
        assertThat(result.traceId()).isEqualTo("trace-salary");
        assertThat(result.salaryMonth()).isEqualTo("2026-06");
    }
}
