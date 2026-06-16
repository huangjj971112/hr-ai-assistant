package com.example.hrai.ai.multiagent;

import com.example.hrai.ai.mcp.client.HrMcpCaller;
import com.example.hrai.ai.security.ToolTokenService;
import com.example.hrai.ai.tool.ToolResult;
import com.example.hrai.dto.knowledge.KnowledgeAskResponse;
import com.example.hrai.security.CurrentUserService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 基于 MCP Tool 的制度子 Agent 实现。
 *
 * <p>复用 query_leave_policy 背后的制度 RAG/知识库能力。它只返回制度摘要和证据，
 * 不直接推导薪酬影响，避免制度查询和薪酬判断混在一起。</p>
 */
@Service
public class McpPolicyAgent extends AbstractMcpSubAgent implements PolicyAgent {

    private static final Set<String> SCOPES = Set.of("leave:policy:read");

    private final HrMcpCaller hrMcpCaller;

    public McpPolicyAgent(
            HrMcpCaller hrMcpCaller,
            CurrentUserService currentUserService,
            ToolTokenService toolTokenService
    ) {
        super(currentUserService, toolTokenService);
        this.hrMcpCaller = hrMcpCaller;
    }

    @Override
    public PolicyAgentResult query(AgentInvocationContext context) {
        // 直接把用户原始问题交给制度查询，尽量保留“病假/年假/工资”等上下文。
        ToolResult<Object> result = hrMcpCaller.call("query_leave_policy", Map.of(
                "toolToken", toolToken(SCOPES),
                "question", context.message()
        ));
        return new PolicyAgentResult(result.success(), answer(result.data()), evidence(result.data()), result.traceId());
    }

    private String answer(Object data) {
        if (data instanceof KnowledgeAskResponse response) {
            return response.getAnswer();
        }
        if (data instanceof Map<?, ?> map) {
            Object answer = map.get("answer");
            return answer == null ? "" : answer.toString();
        }
        return data == null ? "" : data.toString();
    }

    private List<String> evidence(Object data) {
        if (data instanceof KnowledgeAskResponse response && response.getSource() != null) {
            return List.of(response.getSource());
        }
        if (data instanceof Map<?, ?> map && map.get("source") != null) {
            return List.of(map.get("source").toString());
        }
        return List.of();
    }
}
