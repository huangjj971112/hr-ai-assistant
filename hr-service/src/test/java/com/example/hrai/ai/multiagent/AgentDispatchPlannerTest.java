package com.example.hrai.ai.multiagent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentDispatchPlannerTest {

    private final AgentDispatchPlanner planner = new AgentDispatchPlanner();

    @Test
    void shouldPlanLeavePolicyAndSalaryAgentsForAnnualLeaveSalaryQuestion() {
        AgentDispatchPlan plan = planner.plan("我下周想请三天年假，会不会影响工资？");

        assertThat(plan.needPolicy()).isTrue();
        assertThat(plan.needLeave()).isTrue();
        assertThat(plan.needSalary()).isTrue();
        assertThat(plan.needAttendance()).isFalse();
        assertThat(plan.allowWriteAction()).isFalse();
    }

    @Test
    void shouldPlanPolicyAndSalaryAgentsForSickLeaveSalaryQuestion() {
        AgentDispatchPlan plan = planner.plan("我想请病假，会不会扣工资？");

        assertThat(plan.needPolicy()).isTrue();
        assertThat(plan.needSalary()).isTrue();
        assertThat(plan.needLeave()).isFalse();
        assertThat(plan.needAttendance()).isFalse();
    }

    @Test
    void shouldPlanAttendancePolicyAndSalaryAgentsForLateSalaryQuestion() {
        AgentDispatchPlan plan = planner.plan("我这周迟到了两次，会影响工资吗？");

        assertThat(plan.needAttendance()).isTrue();
        assertThat(plan.needPolicy()).isTrue();
        assertThat(plan.needSalary()).isTrue();
        assertThat(plan.needLeave()).isFalse();
    }
}
