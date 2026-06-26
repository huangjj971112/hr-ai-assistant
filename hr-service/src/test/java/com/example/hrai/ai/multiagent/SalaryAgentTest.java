package com.example.hrai.ai.multiagent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SalaryAgentTest {

    private final SalaryAgent salaryAgent = new SalaryAgent();

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
}
