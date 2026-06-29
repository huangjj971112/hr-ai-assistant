package com.example.hrai.ai.observation;

import java.util.List;

/**
 * 单次 Agent 请求的完整观测快照。
 *
 * @param requestId 单次聊天请求的观测 ID，用于前端展示和日志关联
 * @param status 整体执行状态，由所有 Agent 步骤和 Tool 调用状态汇总得出
 * @param totalDurationMs 本次请求从开始到生成快照的总耗时，单位毫秒
 * @param summarySteps 面向用户可读的步骤摘要，只保留非空摘要
 * @param steps 按执行顺序排列的 Agent 步骤观测
 * @param planner Planner 观测信息；单 Agent 模型链路没有 Planner 时为空
 * @param reflection Hybrid Reflection 观测信息；尚未执行反思时为空
 * @param decision Coordinator 或模型最终决策；没有决策时为空
 */
public record AgentObservationSnapshot(
        String requestId,
        AgentObservationStatus status,
        long totalDurationMs,
        List<String> summarySteps,
        List<AgentObservationStep> steps,
        AgentPlannerObservation planner,
        AgentReflectionObservation reflection,
        AgentDecisionObservation decision
) {

    /**
     * 统一空集合语义并隔离调用方的后续修改。
     */
    public AgentObservationSnapshot {
        summarySteps = summarySteps == null ? List.of() : List.copyOf(summarySteps);
        steps = steps == null ? List.of() : List.copyOf(steps);
    }
}
