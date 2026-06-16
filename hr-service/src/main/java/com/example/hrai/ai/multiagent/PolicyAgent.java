package com.example.hrai.ai.multiagent;

/**
 * 制度子 Agent。
 *
 * <p>只负责查询制度文本/RAG 结果，不直接判断工资影响，也不创建请假申请。</p>
 */
public interface PolicyAgent {

    PolicyAgentResult query(AgentInvocationContext context);
}
