package com.example.hrai.dto.ai;

import com.example.hrai.ai.observation.AgentObservationSnapshot;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AiChatResponse {

    /**
     * 本次回复的意图或处理链路标识，例如 MCP_MODEL_RESPONSE、MULTI_AGENT_RESPONSE。
     */
    private String intent;

    /**
     * 面向前端和用户展示的自然语言回复。
     */
    private String reply;

    /**
     * 结构化业务数据，例如请假 pending、MultiAgentResult 或查询列表。
     */
    private Object data;

    /**
     * 可选的 Agent 执行观测快照，用于右侧观测面板展示 Agent/Tool 调用链。
     */
    private AgentObservationSnapshot observation;

    public AiChatResponse(String intent, String reply, Object data) {
        this(intent, reply, data, null);
    }

    public AiChatResponse(
            String intent,
            String reply,
            Object data,
            AgentObservationSnapshot observation
    ) {
        this.intent = intent;
        this.reply = reply;
        this.data = data;
        this.observation = observation;
    }
}
