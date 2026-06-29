package com.example.hrai.dto.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiChatRequest {

    /**
     * 用户在 Agent 输入框中发送的自然语言问题。
     *
     * <p>例如：查询我的年假余额、我想请病假会不会扣工资、帮我请明天一天年假。</p>
     */
    @NotBlank
    private String message;

    /**
     * 前端生成或传入的会话 ID。
     *
     * <p>后端用它隔离多轮上下文，例如请假 pending、确认/取消、缺槽位补全。
     * 为空时 HrMcpChatService 会使用 default-session。</p>
     */
    private String sessionId;
}
