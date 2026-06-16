package com.example.hrai.ai.service;

import com.example.hrai.exception.BusinessException;

import java.util.HashSet;
import java.util.Set;

/**
 * 限制单次模型请求中的 Tool Calling 次数，防止模型重复规划造成费用失控。
 */
final class AgentToolCallGuard {

    private final int maxCalls;
    private final Set<String> calledTools = new HashSet<>();
    private int callCount;

    AgentToolCallGuard(int maxCalls) {
        this.maxCalls = maxCalls;
    }

    void check(String toolName) {
        if (calledTools.contains(toolName)) {
            throw new BusinessException("MCP_REPEATED_TOOL_CALL", "同一工具在本轮对话中不能重复调用");
        }
        if (callCount >= maxCalls) {
            throw new BusinessException("MCP_TOOL_CALL_LIMIT_EXCEEDED", "本轮对话的工具调用次数已达上限");
        }
        calledTools.add(toolName);
        callCount++;
    }

    int callCount() {
        return callCount;
    }
}
