package com.example.hrai.ai.multiagent;

/**
 * 假期子 Agent。
 *
 * <p>负责假期余额和请假 pending 创建。真正提交申请仍然只能由既有
 * confirm_leave_apply 流程完成，不能在这里直接提交。</p>
 */
public interface LeaveAgent {

    LeaveAgentResult evaluate(AgentInvocationContext context, AgentDispatchPlan plan);
}
