package com.example.hrai.ai.observation;

/**
 * Planner 观测信息，用于前端展示本次 Coordinator 是如何决定调用哪些子 Agent 的。
 *
 * @param plannerType 实际采用的 Planner 类型，例如 LLM 或 RULE
 * @param traceId Planner 日志 traceId，用于和后端日志串联
 * @param fallbackReason LLM Planner 回退到规则 Planner 的原因；未回退时为空
 * @param summary 最终执行计划的简要说明
 */
public record AgentPlannerObservation(
        String plannerType,
        String traceId,
        String fallbackReason,
        String summary
) {
}
