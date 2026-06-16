package com.example.hrai.ai.mcp.audit;

import com.example.hrai.repository.McpToolAuditLogRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class McpToolAuditServiceTest {

    @Test
    void shouldPersistSanitizedAuditWithoutTokenOrReason() {
        McpToolAuditLogRepository repository = mock(McpToolAuditLogRepository.class);
        McpToolAuditService service = new McpToolAuditService(
                repository,
                Clock.fixed(Instant.parse("2026-06-15T04:00:00Z"), ZoneId.of("Asia/Shanghai"))
        );

        service.record(new McpToolAuditEvent(
                "trace-1", "create_leave_pending", "demo-tenant", 1L, "session-1", "pending-1",
                "SUCCESS", null, 12L,
                Map.of("toolToken", "secret", "reason", "private reason", "message", "帮我请假")
        ));

        ArgumentCaptor<com.example.hrai.entity.McpToolAuditLog> captor =
                ArgumentCaptor.forClass(com.example.hrai.entity.McpToolAuditLog.class);
        verify(repository).insert(captor.capture());
        assertThat(captor.getValue().getTraceId()).isEqualTo("trace-1");
        assertThat(captor.getValue().getArgumentSummary()).doesNotContain("secret", "private reason", "帮我请假");
        assertThat(captor.getValue().getArgumentSummary()).contains("message");
    }
}
