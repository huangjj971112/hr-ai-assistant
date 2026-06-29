package com.example.hrai.ai.service;

import com.example.hrai.ai.multiagent.CoordinatorAgent;
import com.example.hrai.dto.ai.AiChatResponse;
import com.example.hrai.security.AuthenticatedUser;
import com.example.hrai.security.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * MCP 对话入口服务。
 *
 * <p>这是 /api/ai/mcp/chat 的第一层业务入口。它不直接调用 MCP Tool，
 * 只负责把一次用户输入整理成“适合 Agent 处理的消息”，再决定交给哪条 Agent 链路。</p>
 *
 * <p>当前分流规则：
 * 复合薪酬影响类问题交给 Multi-Agent Coordinator；
 * 其他 HR 问题交给 SpringAiMcpModelAgent，由大模型自主选择 MCP Tool。</p>
 */
@Service
public class HrMcpChatService {

    private final McpModelAgent modelAgent;
    private final CoordinatorAgent coordinatorAgent;
    private final CurrentUserService currentUserService;
    private final PendingLeaveClarificationMemory clarificationMemory;

    public HrMcpChatService(
            McpModelAgent modelAgent,
            CoordinatorAgent coordinatorAgent,
            CurrentUserService currentUserService,
            PendingLeaveClarificationMemory clarificationMemory
    ) {
        this.modelAgent = modelAgent;
        this.coordinatorAgent = coordinatorAgent;
        this.currentUserService = currentUserService;
        this.clarificationMemory = clarificationMemory;
    }

    public AiChatResponse chat(String message, String sessionId) {
        /*
         * sessionId 是多轮对话的边界。pending 请假、确认/取消、缺槽位补全都按
         * userId + sessionId 隔离，避免 A 会话的问题污染 B 会话。
         */
        String normalizedMessage = message == null ? "" : message.trim();
        String resolvedSessionId = StringUtils.hasText(sessionId) ? sessionId.trim() : "default-session";
        AuthenticatedUser user = currentUserService.currentUser();
        // 如果上一轮是“帮我请明天的假”，这一轮是“年假，一天”，这里会合并成完整请求再进入模型。
        String effectiveMessage = resolveLeaveClarification(user, resolvedSessionId, normalizedMessage);
        if (coordinatorAgent.supports(effectiveMessage)) {
            return coordinatorAgent.chat(effectiveMessage, resolvedSessionId);
        }
        return modelAgent.chat(effectiveMessage, resolvedSessionId);
    }

    private String resolveLeaveClarification(AuthenticatedUser user, String sessionId, String message) {
        /*
         * 用户补充“年假/病假/事假”等短句时，优先尝试消费上一轮缺请假类型的草稿。
         * 注意：这里保存的是“缺槽位上下文”，不是请假 pending，不会触发真实提交。
         */
        if (looksLikeLeaveClarificationAnswer(message)) {
            return clarificationMemory.consume(user.userId(), sessionId)
                    .map(original -> original + "，请假类型是" + message)
                    .orElse(message);
        }
        if (looksLikeLeaveRequestMissingType(message)) {
            clarificationMemory.save(user.userId(), sessionId, message);
        }
        return message;
    }

    private boolean looksLikeLeaveRequestMissingType(String message) {
        // 只处理“有请假意图 + 有日期 + 没有类型”的场景，避免把普通余额查询误当作请假草稿。
        return StringUtils.hasText(message)
                && containsAny(message, "请假", "帮我请", "我想请", "休假", "请明天", "请后天")
                && containsAny(message, "今天", "明天", "后天", "下周", "202", "年", "月", "日")
                && !containsAny(message, "年假", "病假", "事假");
    }

    private boolean looksLikeLeaveClarificationAnswer(String message) {
        return StringUtils.hasText(message)
                && message.length() <= 20
                && containsAny(message, "年假", "病假", "事假")
                && !containsAny(message, "查询", "余额", "制度", "会不会", "影响", "工资");
    }

    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
