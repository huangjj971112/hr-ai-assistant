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
 * <p>浏览器仍使用普通登录 JWT；短期 Tool Token 和 MCP 协议调用均在后端完成。</p>
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
        return hrMcpChatService.chat(request.getMessage(), request.getSessionId());
    }
}
