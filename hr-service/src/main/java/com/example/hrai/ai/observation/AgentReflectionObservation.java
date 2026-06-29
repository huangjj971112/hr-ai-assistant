package com.example.hrai.ai.observation;

/**
 * Hybrid Reflection 观测信息，用于解释最终回答是否被反思层放行、拦截或建议重试。
 *
 * @param traceId Reflection 日志 traceId
 * @param ruleAction 规则反思层的动作
 * @param action 最终反思动作
 * @param reason 最终动作原因，主要给调试和学习使用
 * @param needRetry 是否建议重试
 * @param needReplan 是否建议重新规划
 * @param llmRawOutput LLM Reflection 原始输出；规则层未放行或模型不可用时可能为空
 */
public record AgentReflectionObservation(
        String traceId,
        String ruleAction,
        String action,
        String reason,
        boolean needRetry,
        boolean needReplan,
        String llmRawOutput
) {
}
