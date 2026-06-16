package com.example.hrai.ai.tool;

import com.example.hrai.ai.dto.AttendanceAgentToolRequest;
import com.example.hrai.ai.dto.ConfirmedLeaveApplyToolRequest;
import com.example.hrai.ai.dto.LeaveBalanceAgentToolRequest;
import com.example.hrai.ai.dto.LeavePolicyAgentToolRequest;
import com.example.hrai.ai.memory.AgentMemoryService;
import com.example.hrai.ai.memory.PendingLeaveApplyDTO;
import com.example.hrai.dto.attendance.AttendanceRecordResponse;
import com.example.hrai.dto.knowledge.KnowledgeAskResponse;
import com.example.hrai.dto.leave.LeaveApplyRequest;
import com.example.hrai.dto.leave.LeaveApplyResponse;
import com.example.hrai.dto.leave.LeaveBalanceResponse;
import com.example.hrai.exception.BusinessException;
import com.example.hrai.security.AuthenticatedUser;
import com.example.hrai.security.CurrentUserService;
import com.example.hrai.service.ai.tools.AttendanceTools;
import com.example.hrai.service.ai.tools.KnowledgeTools;
import com.example.hrai.service.ai.tools.LeaveTools;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.function.Supplier;

@Component
public class HrAgentTools {

    private final CurrentUserService currentUserService;
    private final AgentMemoryService agentMemoryService;
    private final LeaveTools leaveTools;
    private final AttendanceTools attendanceTools;
    private final KnowledgeTools knowledgeTools;

    public HrAgentTools(
            CurrentUserService currentUserService,
            AgentMemoryService agentMemoryService,
            LeaveTools leaveTools,
            AttendanceTools attendanceTools,
            KnowledgeTools knowledgeTools
    ) {
        this.currentUserService = currentUserService;
        this.agentMemoryService = agentMemoryService;
        this.leaveTools = leaveTools;
        this.attendanceTools = attendanceTools;
        this.knowledgeTools = knowledgeTools;
    }

    @Tool(description = "查询当前登录员工的年假和病假余额")
    public ToolResult<LeaveBalanceResponse> queryLeaveBalance(LeaveBalanceAgentToolRequest request) {
        return execute("假期余额查询成功", () -> {
            AuthenticatedUser user = currentUserService.currentUser();
            requireCurrentEmployee(user, request.employeeName());
            return leaveTools.queryLeaveBalance(user.employeeName());
        });
    }

    @Tool(description = "查询当前登录员工指定日期范围内的考勤记录")
    public ToolResult<List<AttendanceRecordResponse>> queryAttendance(AttendanceAgentToolRequest request) {
        return execute("考勤查询成功", () -> {
            AuthenticatedUser user = currentUserService.currentUser();
            requireCurrentEmployee(user, request.employeeName());
            return attendanceTools.queryAttendanceRecords(user.employeeName(), request.startDate(), request.endDate());
        });
    }

    @Tool(description = "查询员工手册中的请假制度，不得编造制度内容")
    public ToolResult<KnowledgeAskResponse> queryLeavePolicy(LeavePolicyAgentToolRequest request) {
        return execute("请假制度查询成功", () -> {
            String question = StringUtils.hasText(request.question()) ? request.question() : "请假制度";
            return knowledgeTools.askKnowledge(question);
        });
    }

    public ToolResult<LeaveApplyResponse> applyLeave(ConfirmedLeaveApplyToolRequest request) {
        return execute("请假申请已提交，等待审批", () -> {
            AuthenticatedUser user = currentUserService.currentUser();
            if (!Boolean.TRUE.equals(request.confirmed()) || !StringUtils.hasText(request.sessionId())) {
                throw new BusinessException("LEAVE_CONFIRMATION_REQUIRED", "请先确认待提交的请假申请");
            }
            PendingLeaveApplyDTO pending = agentMemoryService.getPendingLeave(user.userId(), request.sessionId())
                    .filter(PendingLeaveApplyDTO::isConfirmed)
                    .orElseThrow(() -> new BusinessException("NO_PENDING_LEAVE_APPLY", "当前没有待确认的申请"));
            if (!user.employeeName().equals(pending.employeeName())) {
                throw new BusinessException("FORBIDDEN", "不允许提交其他员工的请假申请");
            }

            LeaveApplyRequest applyRequest = new LeaveApplyRequest();
            applyRequest.setEmployeeName(pending.employeeName());
            applyRequest.setLeaveType(pending.leaveType());
            applyRequest.setStartTime(pending.startTime());
            applyRequest.setEndTime(pending.endTime());
            applyRequest.setReason(pending.reason());
            LeaveApplyResponse response = leaveTools.applyLeave(applyRequest);
            agentMemoryService.deletePendingLeave(user.userId(), request.sessionId());
            return response;
        });
    }

    private void requireCurrentEmployee(AuthenticatedUser user, String requestedEmployeeName) {
        if (StringUtils.hasText(requestedEmployeeName) && !user.employeeName().equals(requestedEmployeeName.trim())) {
            throw new BusinessException("FORBIDDEN", "员工助手只能操作当前登录员工的数据");
        }
    }

    private <T> ToolResult<T> execute(String successMessage, Supplier<T> operation) {
        try {
            return ToolResult.success(successMessage, operation.get());
        } catch (BusinessException exception) {
            return ToolResult.failure(exception.getCode(), exception.getMessage());
        } catch (RuntimeException exception) {
            return ToolResult.failure("TOOL_CALL_FAILED", "业务工具调用失败，请稍后重试");
        }
    }
}
