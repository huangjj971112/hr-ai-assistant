package com.example.hrai.ai.multiagent;

/**
 * SalaryAgent 的输入快照。
 *
 * <p>薪酬判断只消费其他子 Agent 的结构化结果，不重新调用业务 Service。</p>
 */
public record SalaryAgentInput(
        String message,
        PolicyAgentResult policy,
        AttendanceAgentResult attendance,
        LeaveAgentResult leave
) {
}
