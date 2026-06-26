package com.example.hrai.ai.observation;

/**
 * Agent 最终决策的观测信息。
 *
 * @param outcome 最终判断结果，例如 NO_IMPACT、POSSIBLE_IMPACT 或 UNKNOWN
 * @param basis 形成最终判断的制度依据或事实依据
 * @param needsHumanConfirmation 是否建议 HR 人工确认
 */
public record AgentDecisionObservation(
        String outcome,
        String basis,
        boolean needsHumanConfirmation
) {
}
