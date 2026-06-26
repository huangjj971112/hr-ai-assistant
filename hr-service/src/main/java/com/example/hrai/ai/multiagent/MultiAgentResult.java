package com.example.hrai.ai.multiagent;

/**
 * 一轮 Multi-Agent 协作的完整结构化结果。
 *
 * <p>该对象会放进 {@code AiChatResponse.data}，便于前端调试或后续展示各子 Agent 的证据。</p>
 *
 * @param dispatchPlan Coordinator 生成的调度计划
 * @param policy PolicyAgent 返回的制度查询结果；未调度时为空
 * @param attendance AttendanceAgent 返回的考勤查询结果；未调度时为空
 * @param leave LeaveAgent 返回的假期余额或待确认申请结果；未调度时为空
 * @param salary SalaryAgent 返回的薪酬影响判断结果；未调度时为空
 */
public record MultiAgentResult(
        AgentDispatchPlan dispatchPlan,
        PolicyAgentResult policy,
        AttendanceAgentResult attendance,
        LeaveAgentResult leave,
        SalaryImpactResult salary
) {
}
