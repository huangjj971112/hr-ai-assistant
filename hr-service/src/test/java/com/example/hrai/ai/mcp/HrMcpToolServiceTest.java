package com.example.hrai.ai.mcp;

import com.example.hrai.ai.mcp.dto.CancelPendingMcpRequest;
import com.example.hrai.ai.mcp.dto.ConfirmLeaveApplyMcpRequest;
import com.example.hrai.ai.mcp.dto.CreateLeavePendingMcpRequest;
import com.example.hrai.ai.mcp.dto.QueryAttendanceMcpRequest;
import com.example.hrai.ai.mcp.dto.QueryLeaveBalanceMcpRequest;
import com.example.hrai.ai.mcp.dto.QueryLeavePolicyMcpRequest;
import com.example.hrai.ai.memory.AgentMemoryService;
import com.example.hrai.ai.memory.PendingLeaveApplyDTO;
import com.example.hrai.ai.mcp.audit.McpToolAuditService;
import com.example.hrai.ai.security.ToolTokenContext;
import com.example.hrai.ai.security.ToolTokenService;
import com.example.hrai.dto.knowledge.KnowledgeAskResponse;
import com.example.hrai.dto.leave.LeaveApplyResponse;
import com.example.hrai.dto.leave.LeaveBalanceResponse;
import com.example.hrai.entity.LeaveType;
import com.example.hrai.entity.UserRole;
import com.example.hrai.service.ai.LeaveRequestParser;
import com.example.hrai.service.ai.ParsedLeaveRequest;
import com.example.hrai.service.ai.tools.AttendanceTools;
import com.example.hrai.service.ai.tools.KnowledgeTools;
import com.example.hrai.service.ai.tools.LeaveTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HrMcpToolServiceTest {

    private static final String TOKEN = "tool-token";
    private static final String SESSION_ID = "mcp-session";
    private static final LocalDateTime START = LocalDateTime.of(2026, 6, 16, 9, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 6, 16, 18, 0);

    @Mock
    private ToolTokenService toolTokenService;
    @Mock
    private LeaveTools leaveTools;
    @Mock
    private AttendanceTools attendanceTools;
    @Mock
    private KnowledgeTools knowledgeTools;
    @Mock
    private LeaveRequestParser leaveRequestParser;
    @Mock
    private AgentMemoryService agentMemoryService;
    @Mock
    private McpToolAuditService auditService;

    private HrMcpToolService service;

    @BeforeEach
    void setUp() {
        service = new HrMcpToolService(
                toolTokenService,
                leaveTools,
                attendanceTools,
                knowledgeTools,
                leaveRequestParser,
                agentMemoryService,
                auditService
        );
    }

    @Test
    void shouldQueryBalanceForEmployeeFromToken() {
        allowScopes("leave:balance:read");
        when(leaveTools.queryLeaveBalance("张三")).thenReturn(new LeaveBalanceResponse("张三", 5, 10));

        var result = service.queryLeaveBalance(new QueryLeaveBalanceMcpRequest(TOKEN));

        assertThat(result.success()).isTrue();
        assertThat(result.data().getEmployeeName()).isEqualTo("张三");
        verify(leaveTools).queryLeaveBalance("张三");
    }

    @Test
    void shouldQueryAttendanceForEmployeeFromToken() {
        allowScopes("attendance:records:read");
        LocalDate startDate = LocalDate.of(2026, 6, 8);
        LocalDate endDate = LocalDate.of(2026, 6, 14);
        when(attendanceTools.queryAttendanceRecords("张三", startDate, endDate)).thenReturn(List.of());

        var result = service.queryAttendance(new QueryAttendanceMcpRequest(TOKEN, startDate, endDate));

        assertThat(result.success()).isTrue();
        verify(attendanceTools).queryAttendanceRecords("张三", startDate, endDate);
    }

    @Test
    void shouldQueryLeavePolicyThroughExistingKnowledgeTool() {
        allowScopes("leave:policy:read");
        when(knowledgeTools.askKnowledge("年假可以结转吗"))
                .thenReturn(new KnowledgeAskResponse("可以结转 5 天", "员工手册", "conversation-1"));

        var result = service.queryLeavePolicy(new QueryLeavePolicyMcpRequest(TOKEN, "年假可以结转吗"));

        assertThat(result.success()).isTrue();
        assertThat(result.data().getSource()).isEqualTo("员工手册");
        verify(knowledgeTools).askKnowledge("年假可以结转吗");
    }

    @Test
    void shouldRejectMissingScopeBeforeCallingBusinessTool() {
        allowScopes("attendance:records:read");

        var result = service.queryLeaveBalance(new QueryLeaveBalanceMcpRequest(TOKEN));

        assertThat(result.success()).isFalse();
        assertThat(result.code()).isEqualTo("FORBIDDEN");
        verify(leaveTools, never()).queryLeaveBalance(any());
    }

    @Test
    void shouldCreatePendingWithoutSubmitting() {
        allowScopes("leave:apply");
        ParsedLeaveRequest parsed = new ParsedLeaveRequest(LeaveType.ANNUAL, START, END, "个人事务", List.of());
        PendingLeaveApplyDTO pending = pending(false);
        when(leaveRequestParser.parse("帮我请下周二年假")).thenReturn(parsed);
        when(agentMemoryService.savePendingLeave(any(), any(), any())).thenReturn(pending);

        var result = service.createLeavePending(
                new CreateLeavePendingMcpRequest(TOKEN, SESSION_ID, "帮我请下周二年假")
        );

        assertThat(result.success()).isTrue();
        assertThat(result.data().confirmed()).isFalse();
        assertThat(result.data().startTime()).isEqualTo("2026-06-16 09:00:00");
        verify(leaveTools, never()).applyLeave(any(com.example.hrai.dto.leave.LeaveApplyRequest.class));
    }

    @Test
    void shouldConfirmPendingThenSubmitAndKeepIdempotencyState() {
        allowScopes("leave:apply");
        PendingLeaveApplyDTO confirmed = pending(true);
        when(agentMemoryService.getPendingLeave(1L, SESSION_ID)).thenReturn(Optional.of(pending(false)));
        when(agentMemoryService.confirmPendingLeave(1L, SESSION_ID, "pending-1", 1))
                .thenReturn(Optional.of(confirmed));
        when(leaveTools.applyLeave(any(com.example.hrai.dto.leave.LeaveApplyRequest.class)))
                .thenReturn(new LeaveApplyResponse("LEAVE001", "PENDING", "请假申请已提交"));

        var result = service.confirmLeaveApply(
                new ConfirmLeaveApplyMcpRequest(TOKEN, SESSION_ID, "pending-1", 1, "request-1")
        );

        assertThat(result.success()).isTrue();
        ArgumentCaptor<com.example.hrai.dto.leave.LeaveApplyRequest> requestCaptor =
                ArgumentCaptor.forClass(com.example.hrai.dto.leave.LeaveApplyRequest.class);
        verify(leaveTools).applyLeave(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getEmployeeName()).isEqualTo("张三");
        verify(agentMemoryService).savePendingState(eq(1L), eq(SESSION_ID), any(PendingLeaveApplyDTO.class));
        verify(agentMemoryService, never()).deletePendingLeave(1L, SESSION_ID);
    }

    @Test
    void shouldReturnOriginalResponseForSameIdempotencyKey() {
        allowScopes("leave:apply");
        LeaveApplyResponse submitted = new LeaveApplyResponse("LEAVE001", "PENDING", "请假申请已提交");
        when(agentMemoryService.getPendingLeave(1L, SESSION_ID))
                .thenReturn(Optional.of(pending(false).withSubmitted("request-1", submitted)));

        var result = service.confirmLeaveApply(
                new ConfirmLeaveApplyMcpRequest(TOKEN, SESSION_ID, "pending-1", 3, "request-1")
        );

        assertThat(result.success()).isTrue();
        assertThat(result.data()).isSameAs(submitted);
        verify(leaveTools, never()).applyLeave(any(com.example.hrai.dto.leave.LeaveApplyRequest.class));
    }

    @Test
    void shouldRejectDifferentIdempotencyKeyAfterSubmission() {
        allowScopes("leave:apply");
        LeaveApplyResponse submitted = new LeaveApplyResponse("LEAVE001", "PENDING", "请假申请已提交");
        when(agentMemoryService.getPendingLeave(1L, SESSION_ID))
                .thenReturn(Optional.of(pending(false).withSubmitted("request-1", submitted)));

        var result = service.confirmLeaveApply(
                new ConfirmLeaveApplyMcpRequest(TOKEN, SESSION_ID, "pending-1", 3, "request-2")
        );

        assertThat(result.code()).isEqualTo("IDEMPOTENCY_CONFLICT");
        verify(leaveTools, never()).applyLeave(any(com.example.hrai.dto.leave.LeaveApplyRequest.class));
    }

    @Test
    void shouldRejectStalePendingVersionBeforeSubmitting() {
        allowScopes("leave:apply");
        when(agentMemoryService.getPendingLeave(1L, SESSION_ID)).thenReturn(Optional.of(pending(false)));
        when(agentMemoryService.confirmPendingLeave(1L, SESSION_ID, "pending-1", 9))
                .thenThrow(new com.example.hrai.exception.BusinessException(
                        "PENDING_VERSION_CONFLICT", "待确认申请版本已变化"
                ));

        var result = service.confirmLeaveApply(
                new ConfirmLeaveApplyMcpRequest(TOKEN, SESSION_ID, "pending-1", 9, "request-1")
        );

        assertThat(result.code()).isEqualTo("PENDING_VERSION_CONFLICT");
        assertThat(result.traceId()).isNotBlank();
        verify(leaveTools, never()).applyLeave(any(com.example.hrai.dto.leave.LeaveApplyRequest.class));
    }

    @Test
    void shouldRejectInvalidAttendanceRangeBeforeCallingBusinessTool() {
        allowScopes("attendance:records:read");

        var result = service.queryAttendance(new QueryAttendanceMcpRequest(
                TOKEN, LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 1)
        ));

        assertThat(result.code()).isEqualTo("VALIDATION_FAILED");
        verify(attendanceTools, never()).queryAttendanceRecords(any(), any(), any());
    }

    @Test
    void shouldCancelPendingWithMatchingIdAndVersion() {
        allowScopes("leave:apply");
        when(agentMemoryService.getPendingLeave(1L, SESSION_ID)).thenReturn(Optional.of(pending(false)));

        var result = service.cancelPending(new CancelPendingMcpRequest(TOKEN, SESSION_ID, "pending-1", 1));

        assertThat(result.success()).isTrue();
        verify(agentMemoryService).savePendingState(eq(1L), eq(SESSION_ID), any(PendingLeaveApplyDTO.class));
        verify(agentMemoryService, never()).deletePendingLeave(1L, SESSION_ID);
    }

    private void allowScopes(String... scopes) {
        when(toolTokenService.parseToken(TOKEN)).thenReturn(new ToolTokenContext(
                1L, "zhangsan", "张三", UserRole.EMPLOYEE, "demo-tenant", Set.of(scopes)
        ));
    }

    private PendingLeaveApplyDTO pending(boolean confirmed) {
        return new PendingLeaveApplyDTO(
                "pending_leave_apply", "张三", LeaveType.ANNUAL, START, END, "个人事务",
                confirmed, LocalDateTime.of(2026, 6, 14, 12, 0), LocalDateTime.of(2026, 6, 14, 12, 20),
                "pending-1", confirmed ? 2 : 1, confirmed ? "CONFIRMED" : "PENDING_CONFIRMATION", null, null
        );
    }
}
