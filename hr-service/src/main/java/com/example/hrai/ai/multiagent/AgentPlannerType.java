package com.example.hrai.ai.multiagent;

/**
 * 标识本次调度计划来自哪一种 Planner，便于日志和观测排查。
 */
public enum AgentPlannerType {
    LLM,
    RULE
}
