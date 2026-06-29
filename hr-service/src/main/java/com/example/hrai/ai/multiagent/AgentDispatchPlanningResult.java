package com.example.hrai.ai.multiagent;

/**
 * Planner 的完整执行结果。
 *
 * @param plan Coordinator 最终执行的计划
 * @param plannerType 实际采用的 Planner 类型，LLM 失败回退时为 RULE
 * @param traceId 本次规划 traceId，用于串联日志
 * @param rawOutput LLM 原始输出；规则 Planner 或模型不可用时可能为空
 * @param fallbackReason 回退到规则 Planner 的原因；未回退时为空
 */
public record AgentDispatchPlanningResult(
        AgentDispatchPlan plan,
        AgentPlannerType plannerType,
        String traceId,
        String rawOutput,
        String fallbackReason
) {
}
