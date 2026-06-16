package com.example.hrai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * MCP Tool 审计日志持久化实体，用于通过 traceId 追踪调用结果。
 */
@Data
@TableName("mcp_tool_audit_log")
public class McpToolAuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String traceId;
    private String toolName;
    private String tenantId;
    private Long userId;
    private String sessionId;
    private String pendingId;
    private String status;
    private String errorCode;
    private Long durationMs;
    private String argumentSummary;
    private LocalDateTime createdAt;
}
