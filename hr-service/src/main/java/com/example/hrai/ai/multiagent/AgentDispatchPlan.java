package com.example.hrai.ai.multiagent;

/**
 * Coordinator 的调度计划。
 *
 * @param needPolicy 是否需要制度子 Agent 查询制度/RAG
 * @param needAttendance 是否需要考勤子 Agent 查询考勤记录
 * @param needLeave 是否需要假期子 Agent 查询余额或创建 pending
 * @param needSalary 是否需要薪酬子 Agent 判断工资影响
 * @param allowWriteAction 是否允许触发写操作；即使为 true，也只能创建 pending，不能直接提交
 * @param reason 调度原因，方便测试、日志和前端排查
 */
public record AgentDispatchPlan(
        boolean needPolicy,
        boolean needAttendance,
        boolean needLeave,
        boolean needSalary,
        boolean allowWriteAction,
        String reason
) {
}
