package com.example.hrai.ai.multiagent;

/**
 * Step-based Planner 的单个执行步骤。
 *
 * @param agent 要执行的子 Agent 名称，例如 SalaryAgent
 * @param action 子 Agent 要执行的动作，例如 query_salary
 * @param reason Planner 给出的调度原因，便于日志和前端观测解释
 */
public record AgentDispatchStep(
        String agent,
        String action,
        String reason
) {
}
