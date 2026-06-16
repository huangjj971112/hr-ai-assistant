package com.example.hrai.ai.multiagent;

import com.example.hrai.dto.ai.AiChatResponse;

/**
 * Multi-Agent 协调器入口。
 *
 * <p>Coordinator 只负责“是否接管、调度哪些子 Agent、如何汇总结果”。
 * 它不直接查询数据库、不直接调用 HR Service，也不绕过 pending/confirm 安全流程。</p>
 */
public interface CoordinatorAgent {

    /**
     * 判断当前问题是否适合 Multi-Agent 协作处理。
     */
    boolean supports(String message);

    /**
     * 调度子 Agent 并生成最终面向用户的回复。
     */
    AiChatResponse chat(String message, String sessionId);
}
