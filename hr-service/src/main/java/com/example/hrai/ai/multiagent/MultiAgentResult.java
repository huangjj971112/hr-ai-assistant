package com.example.hrai.ai.multiagent;

/**
 * 一轮 Multi-Agent 协作的完整结构化结果。
 *
 * <p>该对象会放进 {@code AiChatResponse.data}，便于前端调试或后续展示各子 Agent 的证据。</p>
 */
public record MultiAgentResult(
        AgentDispatchPlan dispatchPlan,
        PolicyAgentResult policy,
        AttendanceAgentResult attendance,
        LeaveAgentResult leave,
        SalaryImpactResult salary
) {
}
