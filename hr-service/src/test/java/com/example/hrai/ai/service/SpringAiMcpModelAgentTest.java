package com.example.hrai.ai.service;

import com.example.hrai.ai.mcp.client.HrMcpCaller;
import com.example.hrai.ai.memory.AgentMemoryService;
import com.example.hrai.ai.memory.PendingLeaveApplyDTO;
import com.example.hrai.ai.reflection.ReflectionContext;
import com.example.hrai.ai.reflection.ReflectionResult;
import com.example.hrai.ai.reflection.ReflectionService;
import com.example.hrai.ai.security.ToolTokenService;
import com.example.hrai.ai.tool.ToolResult;
import com.example.hrai.dto.ai.AiChatResponse;
import com.example.hrai.dto.leave.LeaveApplyResponse;
import com.example.hrai.entity.LeaveType;
import com.example.hrai.entity.UserRole;
import com.example.hrai.security.AuthenticatedUser;
import com.example.hrai.security.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpringAiMcpModelAgentTest {

    @Mock
    private ObjectProvider<ChatModel> chatModelProvider;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private ToolTokenService toolTokenService;

    @Mock
    private HrMcpCaller hrMcpCaller;

    @Mock
    private AgentMemoryService memoryService;

    @Mock
    private ReflectionService reflectionService;

    @Test
    void shouldConfirmPendingLeaveDirectlyWithoutAskingModel() {
        SpringAiMcpModelAgent agent = new SpringAiMcpModelAgent(
                chatModelProvider,
                currentUserService,
                toolTokenService,
                hrMcpCaller,
                memoryService,
                reflectionService,
                Clock.fixed(Instant.parse("2026-06-28T04:00:00Z"), ZoneId.of("Asia/Shanghai"))
        );
        AuthenticatedUser user = new AuthenticatedUser(1L, "zhangsan", "张三", UserRole.EMPLOYEE);
        PendingLeaveApplyDTO pending = pending();
        when(currentUserService.currentUser()).thenReturn(user);
        when(toolTokenService.createToken(eq(user), eq("demo-tenant"), any())).thenReturn("tool-token");
        when(memoryService.getPendingLeave(1L, "session-1")).thenReturn(Optional.of(pending));
        when(hrMcpCaller.call(eq("confirm_leave_apply"), any())).thenReturn(ToolResult.success(
                "trace-confirm",
                "请假申请已提交，等待审批",
                new LeaveApplyResponse("LV001", "PENDING", "请假申请已提交")
        ));
        when(reflectionService.reflect(any(ReflectionContext.class)))
                .thenReturn(ReflectionResult.pass("测试放行"));

        AiChatResponse response = agent.chat("确认", "session-1");

        assertThat(response.getReply()).contains("请假申请已提交", "LV001");
        verify(chatModelProvider, never()).getIfAvailable();
        ArgumentCaptor<Map<String, Object>> requestCaptor = ArgumentCaptor.forClass(Map.class);
        verify(hrMcpCaller).call(eq("confirm_leave_apply"), requestCaptor.capture());
        assertThat(requestCaptor.getValue())
                .containsEntry("toolToken", "tool-token")
                .containsEntry("sessionId", "session-1")
                .containsEntry("pendingId", "pending-1")
                .containsEntry("expectedVersion", 1)
                .containsKey("idempotencyKey");
    }

    private PendingLeaveApplyDTO pending() {
        return new PendingLeaveApplyDTO(
                "pending_leave_apply",
                "张三",
                LeaveType.ANNUAL,
                LocalDateTime.of(2026, 6, 29, 9, 0),
                LocalDateTime.of(2026, 6, 29, 18, 0),
                "员工通过 AI 助手申请",
                false,
                LocalDateTime.of(2026, 6, 28, 12, 0),
                LocalDateTime.of(2026, 6, 28, 12, 20),
                "pending-1",
                1,
                "PENDING_CONFIRMATION",
                null,
                null
        );
    }
}
