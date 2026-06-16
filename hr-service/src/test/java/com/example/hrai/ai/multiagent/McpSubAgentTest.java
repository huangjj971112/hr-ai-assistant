package com.example.hrai.ai.multiagent;

import com.example.hrai.ai.mcp.client.HrMcpCaller;
import com.example.hrai.ai.security.ToolTokenService;
import com.example.hrai.ai.tool.ToolResult;
import com.example.hrai.entity.UserRole;
import com.example.hrai.security.AuthenticatedUser;
import com.example.hrai.security.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpSubAgentTest {

    @Mock
    private HrMcpCaller hrMcpCaller;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private ToolTokenService toolTokenService;

    private AuthenticatedUser user;

    @BeforeEach
    void setUp() {
        user = new AuthenticatedUser(1L, "zhangsan", "张三", UserRole.EMPLOYEE);
        when(currentUserService.currentUser()).thenReturn(user);
    }

    @Test
    void policyAgentShouldCallQueryLeavePolicyThroughMcp() {
        when(toolTokenService.createToken(user, "demo-tenant", Set.of("leave:policy:read"))).thenReturn("tool-token");
        when(hrMcpCaller.call("query_leave_policy", Map.of("toolToken", "tool-token", "question", "病假会不会扣工资？")))
                .thenReturn(ToolResult.success("trace-policy", "ok", Map.of("answer", "病假工资按制度执行", "source", "员工手册")));

        PolicyAgentResult result = new McpPolicyAgent(hrMcpCaller, currentUserService, toolTokenService)
                .query(new AgentInvocationContext("病假会不会扣工资？", "session-1"));

        assertThat(result.policySummary()).isEqualTo("病假工资按制度执行");
        assertThat(result.evidence()).containsExactly("员工手册");
        assertThat(result.traceId()).isEqualTo("trace-policy");
    }

    @Test
    void attendanceAgentShouldCallQueryAttendanceAndCountLateRecordsThroughMcp() {
        when(toolTokenService.createToken(user, "demo-tenant", Set.of("attendance:records:read"))).thenReturn("tool-token");
        LocalDate startDate = LocalDate.of(2026, 6, 15);
        LocalDate endDate = LocalDate.of(2026, 6, 16);
        when(hrMcpCaller.call("query_attendance", Map.of("toolToken", "tool-token",
                "startDate", startDate, "endDate", endDate)))
                .thenReturn(ToolResult.success("trace-attendance", "ok",
                        List.of(Map.of("status", "LATE"), Map.of("remark", "迟到 10 分钟"))));

        AttendanceAgentResult result = new McpAttendanceAgent(hrMcpCaller, currentUserService, toolTokenService)
                .query(new AgentInvocationContext("这周迟到了两次，会影响工资吗？", "session-1"),
                        new AttendanceAgent.DateRange(startDate, endDate));

        assertThat(result.lateCount()).isEqualTo(2);
        assertThat(result.traceId()).isEqualTo("trace-attendance");
    }

    @Test
    void leaveAgentShouldQueryBalanceAndOnlyCreatePendingWhenPlanAllowsWriteAction() {
        when(toolTokenService.createToken(user, "demo-tenant", Set.of("leave:balance:read", "leave:apply")))
                .thenReturn("tool-token");
        when(hrMcpCaller.call("query_leave_balance", Map.of("toolToken", "tool-token")))
                .thenReturn(ToolResult.success("trace-balance", "ok", Map.of("annualLeaveBalance", 5)));
        when(hrMcpCaller.call("create_leave_pending", Map.of("toolToken", "tool-token",
                "sessionId", "session-1", "message", "帮我请2026-06-23一天年假")))
                .thenReturn(ToolResult.success("trace-pending", "ok", Map.of("pendingId", "pending-1")));

        AgentDispatchPlan plan = new AgentDispatchPlan(true, false, true, true, true, "write");
        LeaveAgentResult result = new McpLeaveAgent(hrMcpCaller, currentUserService, toolTokenService)
                .evaluate(new AgentInvocationContext("帮我请2026-06-23一天年假", "session-1"), plan);

        assertThat(result.leaveBalance()).isEqualTo(Map.of("annualLeaveBalance", 5));
        assertThat(result.pendingId()).isEqualTo("pending-1");
        assertThat(result.traceId()).isEqualTo("trace-pending");
    }
}
