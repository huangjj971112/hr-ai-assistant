package com.example.hrai.ai.multiagent;

import java.util.List;

/**
 * 制度子 Agent 的结构化输出。
 *
 * @param success MCP Tool 是否成功
 * @param policySummary 制度摘要，供 SalaryAgent 作为判断依据
 * @param evidence 来源或证据片段
 * @param traceId MCP Tool 返回的追踪 ID
 */
public record PolicyAgentResult(
        boolean success,
        String policySummary,
        List<String> evidence,
        String traceId
) {
}
