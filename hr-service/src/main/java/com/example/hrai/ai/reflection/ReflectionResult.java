package com.example.hrai.ai.reflection;

/**
 * Reflection 的统一输出。
 *
 * @param action 本次反思建议的动作
 * @param reason 给开发者和日志看的原因
 * @param userMessage 需要面向用户展示的补充消息；PASS 时通常为空
 * @param needRetry 是否建议重试
 * @param needReplan 是否建议重新规划
 * @param traceId ReflectionService 生成的日志 traceId
 * @param ruleAction 规则反思层动作
 * @param llmRawOutput LLM Reflection 原始输出
 */
public record ReflectionResult(
        ReflectionAction action,
        String reason,
        String userMessage,
        boolean needRetry,
        boolean needReplan,
        String traceId,
        ReflectionAction ruleAction,
        String llmRawOutput
) {

    public static ReflectionResult pass(String reason) {
        return new ReflectionResult(ReflectionAction.PASS, reason, "", false, false, "", null, "");
    }

    /**
     * 表示建议重试一次。当前第一阶段只记录动作，不自动重放 Tool。
     */
    public static ReflectionResult retry(String reason) {
        return new ReflectionResult(ReflectionAction.RETRY, reason, "", true, false, "", null, "");
    }

    /**
     * 表示当前结果不足以继续，需要向用户追问。
     */
    public static ReflectionResult askUser(String reason, String userMessage) {
        return new ReflectionResult(ReflectionAction.ASK_USER, reason, userMessage, false, false, "", null, "");
    }

    /**
     * 表示不能继续返回原答案，应该给用户明确失败原因。
     */
    public static ReflectionResult fail(String reason, String userMessage) {
        return new ReflectionResult(ReflectionAction.FAIL, reason, userMessage, false, false, "", null, "");
    }

    /**
     * 补充 ReflectionService 执行时生成的观测字段。
     */
    public ReflectionResult withObservation(String traceId, ReflectionAction ruleAction, String llmRawOutput) {
        return new ReflectionResult(
                action, reason, userMessage, needRetry, needReplan, traceId, ruleAction, llmRawOutput
        );
    }

    public ReflectionResult {
        // 防御性兜底，避免 LLM 或测试构造出 null 字段导致返回链路 NPE。
        action = action == null ? ReflectionAction.PASS : action;
        reason = reason == null ? "" : reason;
        userMessage = userMessage == null ? "" : userMessage;
        traceId = traceId == null ? "" : traceId;
        llmRawOutput = llmRawOutput == null ? "" : llmRawOutput;
    }
}
