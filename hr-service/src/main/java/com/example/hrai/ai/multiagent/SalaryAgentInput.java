package com.example.hrai.ai.multiagent;

/**
 * SalaryAgent 的输入快照。
 *
 * <p>薪酬判断只消费其他子 Agent 的结构化结果，不重新调用业务 Service。</p>
 *
 * @param message 用户原始问题，用于识别年假、病假、迟到等薪酬影响场景
 * @param policy PolicyAgent 查询到的制度依据
 * @param attendance AttendanceAgent 查询到的考勤事实
 * @param leave LeaveAgent 查询到的假期事实或 pending 结果
 */
public record SalaryAgentInput(
        String message,
        PolicyAgentResult policy,
        AttendanceAgentResult attendance,
        LeaveAgentResult leave
) {
}
