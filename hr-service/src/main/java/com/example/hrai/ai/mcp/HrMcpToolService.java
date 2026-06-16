package com.example.hrai.ai.mcp;

import com.example.hrai.ai.mcp.dto.CancelPendingMcpRequest;
import com.example.hrai.ai.mcp.dto.ConfirmLeaveApplyMcpRequest;
import com.example.hrai.ai.mcp.dto.CreateLeavePendingMcpRequest;
import com.example.hrai.ai.mcp.dto.PendingLeaveMcpResult;
import com.example.hrai.ai.mcp.dto.QueryAttendanceMcpRequest;
import com.example.hrai.ai.mcp.dto.QueryLeaveBalanceMcpRequest;
import com.example.hrai.ai.mcp.dto.QueryLeavePolicyMcpRequest;
import com.example.hrai.ai.mcp.audit.McpToolAuditEvent;
import com.example.hrai.ai.mcp.audit.McpToolAuditService;
import com.example.hrai.ai.memory.AgentMemoryService;
import com.example.hrai.ai.memory.PendingLeaveApplyDTO;
import com.example.hrai.ai.security.ToolTokenContext;
import com.example.hrai.ai.security.ToolTokenService;
import com.example.hrai.ai.tool.ToolResult;
import com.example.hrai.dto.attendance.AttendanceRecordResponse;
import com.example.hrai.dto.knowledge.KnowledgeAskResponse;
import com.example.hrai.dto.leave.LeaveApplyRequest;
import com.example.hrai.dto.leave.LeaveApplyResponse;
import com.example.hrai.dto.leave.LeaveBalanceResponse;
import com.example.hrai.exception.BusinessException;
import com.example.hrai.service.ai.LeaveRequestParser;
import com.example.hrai.service.ai.ParsedLeaveRequest;
import com.example.hrai.service.ai.tools.AttendanceTools;
import com.example.hrai.service.ai.tools.KnowledgeTools;
import com.example.hrai.service.ai.tools.LeaveTools;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * HR MCP Tool 的统一业务编排层。
 *
 * <p>负责请求校验、Tool Token 权限检查、pending 状态机、幂等控制、
 * traceId 与审计记录。真实 HR 业务仍只调用已有 LeaveTools、
 * AttendanceTools 和 KnowledgeTools，不在 MCP 层重复实现。</p>
 */
@Service
public class HrMcpToolService {

    private static final String LEAVE_BALANCE_READ = "leave:balance:read";
    private static final String ATTENDANCE_READ = "attendance:records:read";
    private static final String LEAVE_POLICY_READ = "leave:policy:read";
    private static final String LEAVE_APPLY = "leave:apply";

    private final ToolTokenService toolTokenService;
    private final LeaveTools leaveTools;
    private final AttendanceTools attendanceTools;
    private final KnowledgeTools knowledgeTools;
    private final LeaveRequestParser leaveRequestParser;
    private final AgentMemoryService agentMemoryService;
    private final McpToolAuditService auditService;

    public HrMcpToolService(
            ToolTokenService toolTokenService,
            LeaveTools leaveTools,
            AttendanceTools attendanceTools,
            KnowledgeTools knowledgeTools,
            LeaveRequestParser leaveRequestParser,
            AgentMemoryService agentMemoryService,
            McpToolAuditService auditService
    ) {
        this.toolTokenService = toolTokenService;
        this.leaveTools = leaveTools;
        this.attendanceTools = attendanceTools;
        this.knowledgeTools = knowledgeTools;
        this.leaveRequestParser = leaveRequestParser;
        this.agentMemoryService = agentMemoryService;
        this.auditService = auditService;
    }

    public ToolResult<LeaveBalanceResponse> queryLeaveBalance(QueryLeaveBalanceMcpRequest request) {
        return execute("query_leave_balance", "假期余额查询成功", arguments(request), () -> {
            requireRequest(request);
            ToolTokenContext caller = caller(request.toolToken(), LEAVE_BALANCE_READ);
            return leaveTools.queryLeaveBalance(caller.employeeName());
        });
    }

    public ToolResult<List<AttendanceRecordResponse>> queryAttendance(QueryAttendanceMcpRequest request) {
        return execute("query_attendance", "考勤查询成功", arguments(request), () -> {
            requireRequest(request);
            if (request.startDate() == null || request.endDate() == null || request.startDate().isAfter(request.endDate())) {
                throw new BusinessException("VALIDATION_FAILED", "考勤查询日期范围无效");
            }
            ToolTokenContext caller = caller(request.toolToken(), ATTENDANCE_READ);
            return attendanceTools.queryAttendanceRecords(caller.employeeName(), request.startDate(), request.endDate());
        });
    }

    public ToolResult<KnowledgeAskResponse> queryLeavePolicy(QueryLeavePolicyMcpRequest request) {
        return execute("query_leave_policy", "请假制度查询成功", arguments(request), () -> {
            requireRequest(request);
            caller(request.toolToken(), LEAVE_POLICY_READ);
            requireText(request.question(), "question");
            return knowledgeTools.askKnowledge(request.question().trim());
        });
    }

    public ToolResult<PendingLeaveMcpResult> createLeavePending(CreateLeavePendingMcpRequest request) {
        return execute("create_leave_pending", "待确认请假申请已创建", arguments(request), () -> {
            requireRequest(request);
            ToolTokenContext caller = caller(request.toolToken(), LEAVE_APPLY);
            requireSessionId(request.sessionId());
            requireText(request.message(), "message");
            ParsedLeaveRequest parsed = leaveRequestParser.parse(request.message());
            if (!parsed.complete()) {
                throw new BusinessException(
                        "LEAVE_APPLY_MISSING_PARAMS",
                        "请补充" + String.join("、", parsed.missingFields())
                );
            }
            PendingLeaveApplyDTO pending = agentMemoryService.savePendingLeave(
                    caller.userId(),
                    request.sessionId(),
                    new PendingLeaveApplyDTO(
                            "pending_leave_apply",
                            caller.employeeName(),
                            parsed.leaveType(),
                            parsed.startTime(),
                            parsed.endTime(),
                            parsed.reason(),
                            false,
                            null,
                            null
                    )
            );
            return PendingLeaveMcpResult.from(pending);
        });
    }

    public ToolResult<LeaveApplyResponse> confirmLeaveApply(ConfirmLeaveApplyMcpRequest request) {
        return execute("confirm_leave_apply", "请假申请已提交，等待审批", arguments(request), () -> {
            requireRequest(request);
            ToolTokenContext caller = caller(request.toolToken(), LEAVE_APPLY);
            requireSessionId(request.sessionId());
            requireText(request.pendingId(), "pendingId");
            requireText(request.idempotencyKey(), "idempotencyKey");
            if (request.expectedVersion() == null || request.expectedVersion() < 1) {
                throw new BusinessException("VALIDATION_FAILED", "expectedVersion 必须大于 0");
            }
            PendingLeaveApplyDTO current = agentMemoryService.getPendingLeave(caller.userId(), request.sessionId())
                    .orElseThrow(() -> new BusinessException("PENDING_NOT_FOUND", "当前没有待确认的申请"));
            if (!request.pendingId().equals(current.pendingId())) {
                throw new BusinessException("PENDING_NOT_FOUND", "待确认申请不存在");
            }
            if ("SUBMITTED".equals(current.status())) {
                // 相同幂等键返回首次提交结果；不同幂等键表示另一请求试图重复提交。
                if (request.idempotencyKey().equals(current.idempotencyKey())) {
                    return current.submittedResponse();
                }
                throw new BusinessException("IDEMPOTENCY_CONFLICT", "该待确认申请已使用其他幂等键提交");
            }
            PendingLeaveApplyDTO pending = agentMemoryService.confirmPendingLeave(
                            caller.userId(), request.sessionId(), request.pendingId(), request.expectedVersion())
                    .orElseThrow(() -> new BusinessException("PENDING_NOT_FOUND", "当前没有待确认的申请"));
            if (!caller.employeeName().equals(pending.employeeName())) {
                throw new BusinessException("FORBIDDEN", "不允许提交其他员工的请假申请");
            }

            LeaveApplyRequest applyRequest = new LeaveApplyRequest();
            applyRequest.setEmployeeName(pending.employeeName());
            applyRequest.setLeaveType(pending.leaveType());
            applyRequest.setStartTime(pending.startTime());
            applyRequest.setEndTime(pending.endTime());
            applyRequest.setReason(pending.reason());
            LeaveApplyResponse response = leaveTools.applyLeave(applyRequest);
            // 提交后保留 pending 状态和响应，用于网络超时后的幂等重试。
            agentMemoryService.savePendingState(
                    caller.userId(), request.sessionId(), pending.withSubmitted(request.idempotencyKey(), response)
            );
            return response;
        });
    }

    public ToolResult<PendingLeaveMcpResult> cancelPending(CancelPendingMcpRequest request) {
        return execute("cancel_pending", "待确认请假申请已取消", arguments(request), () -> {
            requireRequest(request);
            ToolTokenContext caller = caller(request.toolToken(), LEAVE_APPLY);
            requireSessionId(request.sessionId());
            requireText(request.pendingId(), "pendingId");
            if (request.expectedVersion() == null || request.expectedVersion() < 1) {
                throw new BusinessException("VALIDATION_FAILED", "expectedVersion 必须大于 0");
            }
            PendingLeaveApplyDTO pending = agentMemoryService.getPendingLeave(caller.userId(), request.sessionId())
                    .orElseThrow(() -> new BusinessException("PENDING_NOT_FOUND", "当前没有待确认的申请"));
            if (!request.pendingId().equals(pending.pendingId())) {
                throw new BusinessException("PENDING_NOT_FOUND", "待确认申请不存在");
            }
            if (request.expectedVersion() != pending.version()) {
                throw new BusinessException("PENDING_VERSION_CONFLICT", "待确认申请版本已变化");
            }
            PendingLeaveApplyDTO cancelled = pending.withCancelled();
            agentMemoryService.savePendingState(caller.userId(), request.sessionId(), cancelled);
            return PendingLeaveMcpResult.from(cancelled);
        });
    }

    private ToolTokenContext caller(String token, String requiredScope) {
        if (!StringUtils.hasText(token)) {
            throw new BusinessException("INVALID_TOOL_TOKEN", "MCP 工具调用令牌不能为空");
        }
        ToolTokenContext caller = toolTokenService.parseToken(token.trim());
        if (!caller.hasScope(requiredScope)) {
            throw new BusinessException("FORBIDDEN", "工具调用令牌缺少权限：" + requiredScope);
        }
        return caller;
    }

    private void requireSessionId(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            throw new BusinessException("INVALID_SESSION_ID", "sessionId 不能为空");
        }
    }

    private void requireRequest(Object request) {
        if (request == null) {
            throw new BusinessException("VALIDATION_FAILED", "请求不能为空");
        }
    }

    private void requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException("VALIDATION_FAILED", field + " 不能为空");
        }
    }

    private <T> ToolResult<T> execute(
            String toolName,
            String successMessage,
            Map<String, Object> arguments,
            Supplier<T> operation
    ) {
        // traceId 覆盖一次完整 Tool 调用，并同时返回给调用方和写入审计表。
        String traceId = UUID.randomUUID().toString();
        long startedAt = System.currentTimeMillis();
        ToolResult<T> result;
        try {
            result = ToolResult.success(traceId, successMessage, operation.get());
        } catch (BusinessException exception) {
            result = ToolResult.failure(traceId, exception.getCode(), exception.getMessage());
        } catch (RuntimeException exception) {
            result = ToolResult.failure(traceId, "MCP_TOOL_CALL_FAILED", "MCP 工具调用失败，请稍后重试");
        }
        audit(toolName, traceId, arguments, result, System.currentTimeMillis() - startedAt);
        return result;
    }

    private void audit(String toolName, String traceId, Map<String, Object> arguments, ToolResult<?> result, long duration) {
        try {
            ToolTokenContext caller = null;
            Object token = arguments.get("toolToken");
            if (token instanceof String value && StringUtils.hasText(value)) {
                caller = toolTokenService.parseToken(value);
            }
            auditService.record(new McpToolAuditEvent(
                    traceId,
                    toolName,
                    caller == null ? null : caller.tenantId(),
                    caller == null ? null : caller.userId(),
                    stringArgument(arguments, "sessionId"),
                    stringArgument(arguments, "pendingId"),
                    result.success() ? "SUCCESS" : "FAILED",
                    result.success() ? null : result.code(),
                    duration,
                    arguments
            ));
        } catch (RuntimeException ignored) {
            // 审计写入失败不能改变本次 Tool 的业务结果。
        }
    }

    private String stringArgument(Map<String, Object> arguments, String name) {
        Object value = arguments.get(name);
        return value == null ? null : value.toString();
    }

    private Map<String, Object> arguments(Object request) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (request instanceof QueryLeaveBalanceMcpRequest value) {
            values.put("toolToken", value.toolToken());
        } else if (request instanceof QueryAttendanceMcpRequest value) {
            values.put("toolToken", value.toolToken());
            values.put("startDate", value.startDate());
            values.put("endDate", value.endDate());
        } else if (request instanceof QueryLeavePolicyMcpRequest value) {
            values.put("toolToken", value.toolToken());
            values.put("question", value.question());
        } else if (request instanceof CreateLeavePendingMcpRequest value) {
            values.put("toolToken", value.toolToken());
            values.put("sessionId", value.sessionId());
            values.put("message", value.message());
        } else if (request instanceof ConfirmLeaveApplyMcpRequest value) {
            values.put("toolToken", value.toolToken());
            values.put("sessionId", value.sessionId());
            values.put("pendingId", value.pendingId());
            values.put("expectedVersion", value.expectedVersion());
            values.put("idempotencyKey", value.idempotencyKey());
        } else if (request instanceof CancelPendingMcpRequest value) {
            values.put("toolToken", value.toolToken());
            values.put("sessionId", value.sessionId());
            values.put("pendingId", value.pendingId());
            values.put("expectedVersion", value.expectedVersion());
        }
        return values;
    }
}
