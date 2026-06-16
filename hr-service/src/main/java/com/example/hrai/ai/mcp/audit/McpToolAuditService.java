package com.example.hrai.ai.mcp.audit;

import com.example.hrai.entity.McpToolAuditLog;
import com.example.hrai.repository.McpToolAuditLogRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP Tool 的统一审计服务。
 *
 * <p>审计记录可通过 traceId 关联一次工具调用。写库前会隐藏令牌、幂等键及
 * 用户原始消息等敏感信息，避免审计表成为敏感数据副本。</p>
 */
@Service
public class McpToolAuditService {

    private final McpToolAuditLogRepository repository;
    private final Clock clock;

    public McpToolAuditService(McpToolAuditLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void record(McpToolAuditEvent event) {
        McpToolAuditLog log = new McpToolAuditLog();
        log.setTraceId(event.traceId());
        log.setToolName(event.toolName());
        log.setTenantId(event.tenantId());
        log.setUserId(event.userId());
        log.setSessionId(event.sessionId());
        log.setPendingId(event.pendingId());
        log.setStatus(event.status());
        log.setErrorCode(event.errorCode());
        log.setDurationMs(event.durationMs());
        log.setArgumentSummary(sanitize(event.arguments()).toString());
        log.setCreatedAt(LocalDateTime.now(clock));
        repository.insert(log);
    }

    private Map<String, Object> sanitize(Map<String, Object> arguments) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        if (arguments == null) {
            return sanitized;
        }
        arguments.forEach((key, value) -> {
            if ("toolToken".equals(key)
                    || "reason".equals(key)
                    || "message".equals(key)
                    || "question".equals(key)
                    || "idempotencyKey".equals(key)) {
                sanitized.put(key, "***");
            } else {
                sanitized.put(key, value);
            }
        });
        return sanitized;
    }
}
