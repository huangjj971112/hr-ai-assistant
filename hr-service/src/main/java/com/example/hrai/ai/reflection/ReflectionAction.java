package com.example.hrai.ai.reflection;

/**
 * Hybrid Reflection 对一次 Agent 执行结果给出的处理建议。
 */
public enum ReflectionAction {
    PASS,
    RETRY,
    REPLAN,
    ASK_USER,
    FAIL
}
