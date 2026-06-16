package com.example.hrai.ai.multiagent;

import com.example.hrai.ai.mcp.client.HrMcpCaller;
import com.example.hrai.ai.security.ToolTokenService;
import com.example.hrai.ai.tool.ToolResult;
import com.example.hrai.security.CurrentUserService;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

/**
 * 基于 MCP Tool 的假期子 Agent 实现。
 *
 * <p>查询余额和创建待确认请假都通过 MCP 进入 HrMcpToolService。即使用户说“提交”，
 * 这里最多调用 create_leave_pending，真正提交仍必须等待用户后续确认。</p>
 */
@Service
public class McpLeaveAgent extends AbstractMcpSubAgent implements LeaveAgent {

    private static final Set<String> SCOPES = Set.of("leave:balance:read", "leave:apply");

    private final HrMcpCaller hrMcpCaller;

    public McpLeaveAgent(
            HrMcpCaller hrMcpCaller,
            CurrentUserService currentUserService,
            ToolTokenService toolTokenService
    ) {
        super(currentUserService, toolTokenService);
        this.hrMcpCaller = hrMcpCaller;
    }

    @Override
    public LeaveAgentResult evaluate(AgentInvocationContext context, AgentDispatchPlan plan) {
        String token = toolToken(SCOPES);
        ToolResult<Object> balance = hrMcpCaller.call("query_leave_balance", Map.of("toolToken", token));
        if (!plan.allowWriteAction()) {
            return new LeaveAgentResult(balance.success(), balance.data(), null, null, balance.traceId());
        }

        // 写操作安全边界：这里只创建 pending，不调用 confirm_leave_apply。
        ToolResult<Object> pending = hrMcpCaller.call("create_leave_pending", Map.of(
                "toolToken", token,
                "sessionId", context.sessionId(),
                "message", context.message()
        ));
        return new LeaveAgentResult(
                pending.success(),
                balance.data(),
                pending.data(),
                pendingId(pending.data()),
                pending.traceId()
        );
    }

    private String pendingId(Object data) {
        if (data instanceof Map<?, ?> map && map.get("pendingId") != null) {
            return map.get("pendingId").toString();
        }
        return null;
    }
}
