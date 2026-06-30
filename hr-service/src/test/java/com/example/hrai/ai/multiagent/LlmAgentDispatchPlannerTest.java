package com.example.hrai.ai.multiagent;

import com.example.hrai.entity.UserRole;
import com.example.hrai.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class LlmAgentDispatchPlannerTest {

    private static final AuthenticatedUser USER = new AuthenticatedUser(
            1L, "zhangsan", "张三", UserRole.EMPLOYEE
    );
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 28, 10, 30);

    @Test
    void shouldPlanSingleAttendanceAgentFromLlmOutput() {
        LlmAgentDispatchPlanner planner = plannerWith("""
                {
                  "needPlan": true,
                  "steps": [
                    {
                      "agent": "AttendanceAgent",
                      "action": "query_attendance",
                      "reason": "用户想查询考勤"
                    }
                  ],
                  "needConfirm": false,
                  "summary": "需要查询考勤"
                }
                """);

        AgentDispatchPlanningResult result = planner.plan("查询我的考勤", USER, NOW);

        assertThat(result.plannerType()).isEqualTo(AgentPlannerType.LLM);
        assertThat(result.fallbackReason()).isNull();
        assertThat(result.plan().needAttendance()).isTrue();
        assertThat(result.plan().needPolicy()).isFalse();
        assertThat(result.plan().needLeave()).isFalse();
        assertThat(result.plan().needSalary()).isFalse();
        assertThat(result.plan().reason()).isEqualTo("需要查询考勤");
    }

    @Test
    void shouldPlanAttendanceAndLeaveAgentsFromLlmOutput() {
        LlmAgentDispatchPlanner planner = plannerWith("""
                {
                  "needPlan": true,
                  "steps": [
                    {
                      "agent": "AttendanceAgent",
                      "action": "query_attendance",
                      "reason": "用户想查询本月考勤"
                    },
                    {
                      "agent": "LeaveAgent",
                      "action": "query_leave_balance",
                      "reason": "用户想查询年假余额"
                    }
                  ],
                  "needConfirm": false,
                  "summary": "需要查询考勤和假期余额"
                }
                """);

        AgentDispatchPlanningResult result = planner.plan("查询我的考勤和年假余额", USER, NOW);

        assertThat(result.plannerType()).isEqualTo(AgentPlannerType.LLM);
        assertThat(result.plan().needAttendance()).isTrue();
        assertThat(result.plan().needLeave()).isTrue();
        assertThat(result.plan().needPolicy()).isFalse();
        assertThat(result.plan().needSalary()).isFalse();
    }

    @Test
    void shouldPlanPolicyAgentForAnnualLeavePolicyQuestion() {
        LlmAgentDispatchPlanner planner = plannerWith("""
                {
                  "needPlan": true,
                  "steps": [
                    {
                      "agent": "PolicyAgent",
                      "action": "query_leave_policy",
                      "reason": "用户想查询年假制度"
                    }
                  ],
                  "needConfirm": false,
                  "summary": "需要查询年假制度"
                }
                """);

        AgentDispatchPlanningResult result = planner.plan("年假制度是什么？", USER, NOW);

        assertThat(result.plannerType()).isEqualTo(AgentPlannerType.LLM);
        assertThat(result.plan().needPolicy()).isTrue();
        assertThat(result.plan().needAttendance()).isFalse();
        assertThat(result.plan().needLeave()).isFalse();
        assertThat(result.plan().needSalary()).isFalse();
    }

    @Test
    void shouldPlanSalaryAgentForSalaryQuestion() {
        LlmAgentDispatchPlanner planner = plannerWith("""
                {
                  "needPlan": true,
                  "steps": [
                    {
                      "agent": "SalaryAgent",
                      "action": "query_salary",
                      "reason": "用户想查询工资"
                    }
                  ],
                  "needConfirm": false,
                  "summary": "需要查询工资"
                }
                """);

        AgentDispatchPlanningResult result = planner.plan("我的工资怎么查？", USER, NOW);

        assertThat(result.plannerType()).isEqualTo(AgentPlannerType.LLM);
        assertThat(result.plan().needSalary()).isTrue();
        assertThat(result.plan().needPolicy()).isFalse();
        assertThat(result.plan().needAttendance()).isFalse();
        assertThat(result.plan().needLeave()).isFalse();
    }

    @Test
    void shouldKeepStepOrderFromLlmOutput() {
        LlmAgentDispatchPlanner planner = plannerWith("""
                {
                  "needPlan": true,
                  "steps": [
                    {
                      "agent": "SalaryAgent",
                      "action": "query_salary",
                      "reason": "用户反馈实发工资和应发工资不一致，需要先查工资明细"
                    },
                    {
                      "agent": "PolicyAgent",
                      "action": "query_leave_policy",
                      "reason": "需要查询薪资扣款制度"
                    }
                  ],
                  "needConfirm": false,
                  "summary": "需要先查工资明细，再查制度依据"
                }
                """);

        AgentDispatchPlanningResult result = planner.plan("我这个月只发了三千，实际应发应该是四千", USER, NOW);

        assertThat(result.plannerType()).isEqualTo(AgentPlannerType.LLM);
        assertThat(result.plan().steps()).extracting(AgentDispatchStep::agent)
                .containsExactly("SalaryAgent", "PolicyAgent");
        assertThat(result.plan().steps()).extracting(AgentDispatchStep::action)
                .containsExactly("query_salary", "query_leave_policy");
        assertThat(result.plan().needSalary()).isTrue();
        assertThat(result.plan().needPolicy()).isTrue();
    }

    @Test
    void shouldFallbackToRulePlannerWhenLlmOutputIsIllegal() {
        LlmAgentDispatchPlanner planner = plannerWith("这不是 JSON");

        AgentDispatchPlanningResult result = planner.plan("我这周迟到了两次，会影响工资吗？", USER, NOW);

        assertThat(result.plannerType()).isEqualTo(AgentPlannerType.RULE);
        assertThat(result.fallbackReason()).isEqualTo("LLM_JSON_PARSE_FAILED");
        assertThat(result.rawOutput()).isEqualTo("这不是 JSON");
        assertThat(result.plan().needAttendance()).isTrue();
        assertThat(result.plan().needPolicy()).isTrue();
        assertThat(result.plan().needSalary()).isTrue();
    }

    @Test
    void shouldFallbackToRulePlannerWhenLlmReturnsUnknownAgent() {
        LlmAgentDispatchPlanner planner = plannerWith("""
                {
                  "needPlan": true,
                  "steps": [
                    {
                      "agent": "UnknownAgent",
                      "action": "query_attendance",
                      "reason": "非法 Agent"
                    }
                  ],
                  "needConfirm": false,
                  "summary": "非法计划"
                }
                """);

        AgentDispatchPlanningResult result = planner.plan("我这周迟到了两次，会影响工资吗？", USER, NOW);

        assertThat(result.plannerType()).isEqualTo(AgentPlannerType.RULE);
        assertThat(result.fallbackReason()).isEqualTo("LLM_UNKNOWN_AGENT_UnknownAgent");
        assertThat(result.plan().needAttendance()).isTrue();
        assertThat(result.plan().needPolicy()).isTrue();
        assertThat(result.plan().needSalary()).isTrue();
    }

    private LlmAgentDispatchPlanner plannerWith(String rawOutput) {
        return new LlmAgentDispatchPlanner(new AgentDispatchPlanner(), new ObjectMapper(), prompt -> rawOutput);
    }
}
