package com.example.hrai.ai.observation;

import java.util.List;

/**
 * 单个 Agent 执行步骤的观测信息。
 *
 * @param agentName Agent 名称，例如 PolicyAgent、SalaryAgent 或 SpringAiMcpModelAgent
 * @param status 当前 Agent 步骤的执行状态
 * @param durationMs 当前 Agent 步骤耗时，单位毫秒
 * @param summary 面向用户可读的步骤摘要
 * @param toolCalls 当前 Agent 步骤中按顺序发生的 Tool 调用
 */
public record AgentObservationStep(
        String agentName,
        AgentObservationStatus status,
        long durationMs,
        String summary,
        List<AgentToolObservation> toolCalls
) {

    /**
     * 统一空集合语义并隔离调用方的后续修改。
     */
    public AgentObservationStep {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }
}
