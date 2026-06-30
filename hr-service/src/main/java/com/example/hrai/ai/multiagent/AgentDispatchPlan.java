package com.example.hrai.ai.multiagent;

import java.util.ArrayList;
import java.util.List;

/**
 * Coordinator 的调度计划。
 *
 * @param needPolicy 是否需要制度子 Agent 查询制度/RAG
 * @param needAttendance 是否需要考勤子 Agent 查询考勤记录
 * @param needLeave 是否需要假期子 Agent 查询余额或创建 pending
 * @param needSalary 是否需要薪酬子 Agent 判断工资影响
 * @param allowWriteAction 是否允许触发写操作；即使为 true，也只能创建 pending，不能直接提交
 * @param reason 调度原因，方便测试、日志和前端排查
 * @param steps Step-based Planner 的执行步骤；Coordinator 优先按这里的顺序调度子 Agent
 */
public record AgentDispatchPlan(
        boolean needPolicy,
        boolean needAttendance,
        boolean needLeave,
        boolean needSalary,
        boolean allowWriteAction,
        String reason,
        List<AgentDispatchStep> steps
) {

    public AgentDispatchPlan(
            boolean needPolicy,
            boolean needAttendance,
            boolean needLeave,
            boolean needSalary,
            boolean allowWriteAction,
            String reason
    ) {
        this(needPolicy, needAttendance, needLeave, needSalary, allowWriteAction, reason,
                defaultSteps(needPolicy, needAttendance, needLeave, needSalary, allowWriteAction));
    }

    public AgentDispatchPlan {
        steps = List.copyOf(steps == null || steps.isEmpty()
                ? defaultSteps(needPolicy, needAttendance, needLeave, needSalary, allowWriteAction)
                : steps);
    }

    private static List<AgentDispatchStep> defaultSteps(
            boolean needPolicy,
            boolean needAttendance,
            boolean needLeave,
            boolean needSalary,
            boolean allowWriteAction
    ) {
        List<AgentDispatchStep> result = new ArrayList<>();
        if (needPolicy) {
            result.add(new AgentDispatchStep("PolicyAgent", "query_leave_policy", "需要查询制度依据"));
        }
        if (needAttendance) {
            result.add(new AgentDispatchStep("AttendanceAgent", "query_attendance", "需要查询考勤事实"));
        }
        if (needLeave) {
            result.add(new AgentDispatchStep(
                    "LeaveAgent",
                    allowWriteAction ? "create_leave_pending" : "query_leave_balance",
                    allowWriteAction ? "需要创建待确认请假申请" : "需要查询假期信息"
            ));
        }
        if (needSalary) {
            result.add(new AgentDispatchStep("SalaryAgent", "evaluate_salary_impact", "需要判断薪酬影响"));
        }
        return result;
    }
}
