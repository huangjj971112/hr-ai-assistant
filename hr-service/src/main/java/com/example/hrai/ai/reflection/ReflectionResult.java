package com.example.hrai.ai.reflection;

/**
 * Reflection 的统一输出。
 *
 * @param action 本次反思建议的动作
 * @param reason 给开发者和日志看的原因
 * @param userMessage 需要面向用户展示的补充消息；PASS 时通常为空
 * @param needRetry 是否建议重试
 * @param needReplan 是否建议重新规划
 */
public record ReflectionResult(
        ReflectionAction action,
        String reason,
        String userMessage,
        boolean needRetry,
        boolean needReplan
) {

    public static ReflectionResult pass(String reason) {
        return new ReflectionResult(ReflectionAction.PASS, reason, "", false, false);
    }

    /**
     * 表示建议重试一次。当前第一阶段只记录动作，不自动重放 Tool。
     */
    public static ReflectionResult retry(String reason) {
        return new ReflectionResult(ReflectionAction.RETRY, reason, "", true, false);
    }

    /**
     * 表示当前结果不足以继续，需要向用户追问。
     */
    public static ReflectionResult askUser(String reason, String userMessage) {
        return new ReflectionResult(ReflectionAction.ASK_USER, reason, userMessage, false, false);
    }

    /**
     * 表示不能继续返回原答案，应该给用户明确失败原因。
     */
    public static ReflectionResult fail(String reason, String userMessage) {
        return new ReflectionResult(ReflectionAction.FAIL, reason, userMessage, false, false);
    }

    public ReflectionResult {
        // 防御性兜底，避免 LLM 或测试构造出 null 字段导致返回链路 NPE。
        action = action == null ? ReflectionAction.PASS : action;
        reason = reason == null ? "" : reason;
        userMessage = userMessage == null ? "" : userMessage;
    }
}
