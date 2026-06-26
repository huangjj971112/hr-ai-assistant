package com.example.hrai.ai.observation;

/**
 * Agent 观测结果状态。
 */
public enum AgentObservationStatus {
    /** 当前请求、Agent 步骤或 Tool 调用已成功完成。 */
    SUCCESS,

    /** 当前请求、Agent 步骤或 Tool 调用失败。 */
    FAILED,

    /** 部分步骤成功、部分步骤失败，或步骤尚未完整结束。 */
    PARTIAL
}
