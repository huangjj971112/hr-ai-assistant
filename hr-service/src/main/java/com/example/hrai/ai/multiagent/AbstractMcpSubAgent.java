package com.example.hrai.ai.multiagent;

import com.example.hrai.ai.security.ToolTokenService;
import com.example.hrai.security.AuthenticatedUser;
import com.example.hrai.security.CurrentUserService;

import java.util.Set;

/**
 * MCP 子 Agent 的公共基类。
 *
 * <p>子 Agent 不直接拿当前登录态去调用业务 Service，而是先生成短期
 * Tool Token，再通过 MCP Tool 进入统一的权限校验、审计和业务编排层。</p>
 */
abstract class AbstractMcpSubAgent {

    private static final String TENANT_ID = "demo-tenant";

    private final CurrentUserService currentUserService;
    private final ToolTokenService toolTokenService;

    protected AbstractMcpSubAgent(CurrentUserService currentUserService, ToolTokenService toolTokenService) {
        this.currentUserService = currentUserService;
        this.toolTokenService = toolTokenService;
    }

    /**
     * 为某个子 Agent 生成最小权限 Tool Token。
     *
     * <p>scope 必须按实际工具需求传入，避免 Multi-Agent 层拿到超出职责的权限。</p>
     */
    protected String toolToken(Set<String> scopes) {
        AuthenticatedUser user = currentUserService.currentUser();
        return toolTokenService.createToken(user, TENANT_ID, scopes);
    }
}
