package com.example.hrai.ai.reflection;

import com.example.hrai.ai.observation.AgentObservationSnapshot;

/**
 * LLM Reflection 和 Rule Reflection 的输入快照。
 *
 * @param originalMessage 用户原始问题
 * @param plannerOutput Planner 输出；模型单 Agent 链路没有 Planner 时可为空
 * @param executedAgents 已执行 Agent/Tool 的观测信息
 * @param finalAnswer 准备返回给用户的答案
 * @param responseData 准备返回给前端的结构化数据，例如 pending 或 MultiAgentResult
 */
public record ReflectionContext(
        String originalMessage,
        Object plannerOutput,
        AgentObservationSnapshot executedAgents,
        String finalAnswer,
        Object responseData
) {
    /*
     * 这个 record 刻意保持“只读快照”语义。
     * Reflection 只判断结果质量，不在这里改写 pending、Tool 调用结果或数据库状态。
     */
}
