package com.example.hrai.ai.controller;

import com.example.hrai.ai.service.HrMcpChatService;
import com.example.hrai.dto.ai.AiChatRequest;
import com.example.hrai.dto.ai.AiChatResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 前端 MCP Agent 的 HTTP 对话入口。
 *
 * <p>对应接口：POST /api/ai/mcp/chat。浏览器只需要携带普通登录 JWT 和用户消息；
 * 短期 Tool Token、MCP 协议调用、pending/confirm 安全控制都在后端完成，
 * 前端不会直接接触 MCP Server。</p>
 */
@RestController
@RequestMapping("/api/ai/mcp")
public class HrMcpChatController {

    private final HrMcpChatService hrMcpChatService;

    public HrMcpChatController(HrMcpChatService hrMcpChatService) {
        this.hrMcpChatService = hrMcpChatService;
    }

    @PostMapping("/chat")
    public AiChatResponse chat(@Valid @RequestBody AiChatRequest request) {
        /*
         * Controller 只做 HTTP 入参接收和响应返回，不写 Agent 调度逻辑。
         * 真正的流程在 HrMcpChatService：
         * 1. 规范化 message/sessionId；
         * 2. 补全多轮请假缺槽位上下文；
         * 3. 判断是否进入 Multi-Agent；
         * 4. 否则交给模型 Agent 自主 Tool Calling。
         */
        return hrMcpChatService.chat(request.getMessage(), request.getSessionId());
    }
}
