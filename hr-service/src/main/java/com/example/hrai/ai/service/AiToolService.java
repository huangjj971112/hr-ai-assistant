package com.example.hrai.ai.service;

import com.example.hrai.ai.dto.AttendanceRecordsToolRequest;
import com.example.hrai.ai.dto.LeaveApplyToolRequest;
import com.example.hrai.ai.dto.LeaveApplyToolResponse;
import com.example.hrai.ai.dto.LeaveBalanceToolRequest;
import com.example.hrai.ai.dto.LeaveRecordsToolRequest;
import com.example.hrai.ai.security.ToolTokenContext;
import com.example.hrai.ai.security.ToolTokenService;
import com.example.hrai.dto.attendance.AttendanceRecordResponse;
import com.example.hrai.dto.leave.LeaveApplyRequest;
import com.example.hrai.dto.leave.LeaveApplicationResponse;
import com.example.hrai.dto.leave.LeaveBalanceResponse;
import com.example.hrai.entity.LeaveType;
import com.example.hrai.entity.UserRole;
import com.example.hrai.exception.BusinessException;
import com.example.hrai.security.AuthenticatedUser;
import com.example.hrai.security.CurrentUserService;
import com.example.hrai.service.ai.tools.AttendanceTools;
import com.example.hrai.service.ai.tools.LeaveTools;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiToolService {

    private final CurrentUserService currentUserService;
    private final ToolTokenService toolTokenService;
    private final HttpServletRequest servletRequest;
    private final LeaveTools leaveTools;
    private final AttendanceTools attendanceTools;

    public LeaveBalanceResponse queryLeaveBalance(LeaveBalanceToolRequest request) {
        ToolCaller caller = currentToolCaller("leave:balance:read", request.getToolToken());
        String employeeName = resolveEmployeeName(caller, request.getEmployeeName());
        return leaveTools.queryLeaveBalance(employeeName);
    }

    public List<LeaveApplicationResponse> queryLeaveRecords(LeaveRecordsToolRequest request) {
        ToolCaller caller = currentToolCaller("leave:records:read", request.getToolToken());
        String employeeName = resolveEmployeeName(caller, request.getEmployeeName());
        LeaveType leaveType = parseLeaveType(request.getLeaveType());
        return leaveTools.queryLeaveApplications(employeeName, request.getYear(), leaveType);
    }

    public List<AttendanceRecordResponse> queryAttendanceRecords(AttendanceRecordsToolRequest request) {
        ToolCaller caller = currentToolCaller("attendance:records:read", request.getToolToken());
        String employeeName = resolveEmployeeName(caller, request.getEmployeeName());
        LocalDate startDate = request.getStartDate();
        LocalDate endDate = request.getEndDate();
        if (startDate == null && endDate == null && request.getAttendanceDate() != null) {
            startDate = request.getAttendanceDate();
            endDate = request.getAttendanceDate();
        }
        return attendanceTools.queryAttendanceRecords(employeeName, startDate, endDate);
    }

    public LeaveApplyToolResponse applyLeave(LeaveApplyToolRequest request) {
        ToolCaller caller = currentToolCaller("leave:apply", request.getToolToken());
        String employeeName = resolveEmployeeName(caller, request.getEmployeeName());
        LeaveType leaveType = parseRequiredLeaveType(request.getLeaveType());

        LeaveApplyRequest applyRequest = new LeaveApplyRequest();
        applyRequest.setEmployeeName(employeeName);
        applyRequest.setLeaveType(leaveType);
        applyRequest.setStartTime(request.getStartTime());
        applyRequest.setEndTime(request.getEndTime());
        applyRequest.setReason(request.getReason());
        leaveTools.validateLeaveApply(applyRequest);

        if (Boolean.TRUE.equals(request.getConfirmed())) {
            throw new BusinessException(
                    "AI_TOOL_CONFIRMATION_NOT_ALLOWED",
                    "AI 工具只能生成请假草稿，请在员工端确认后提交"
            );
        }
        return LeaveApplyToolResponse.draft(
                employeeName, leaveType, request.getStartTime(), request.getEndTime(), request.getReason()
        );
    }

    private ToolCaller currentToolCaller(String requiredScope, String requestToolToken) {
        if (StringUtils.hasText(requestToolToken)) {
            return toolCallerFromToken(requestToolToken.trim(), requiredScope);
        }

        String token = currentBearerToken();
        if (StringUtils.hasText(token)) {
            try {
                return toolCallerFromToken(token, requiredScope);
            } catch (BusinessException exception) {
                if (!"INVALID_TOOL_TOKEN".equals(exception.getCode())) {
                    throw exception;
                }
            }
        }

        AuthenticatedUser user = currentUserService.currentUser();
        return new ToolCaller(user.userId(), user.username(), user.employeeName(), user.role(), "demo-tenant");
    }

    private ToolCaller toolCallerFromToken(String token, String requiredScope) {
        ToolTokenContext context = toolTokenService.parseToken(token);
        if (!context.hasScope(requiredScope)) {
            throw new BusinessException("FORBIDDEN", "工具调用令牌缺少权限：" + requiredScope);
        }
        return new ToolCaller(
                context.userId(),
                context.username(),
                context.employeeName(),
                context.role(),
                context.tenantId()
        );
    }

    private String resolveEmployeeName(ToolCaller caller, String requestedEmployeeName) {
        String targetEmployeeName = StringUtils.hasText(requestedEmployeeName)
                ? requestedEmployeeName.trim()
                : caller.employeeName();
        if (caller.role() == UserRole.EMPLOYEE && !caller.employeeName().equals(targetEmployeeName)) {
            throw new BusinessException("FORBIDDEN", "员工只能查询或操作自己的信息");
        }
        return targetEmployeeName;
    }

    private LeaveType parseLeaveType(String leaveType) {
        if (!StringUtils.hasText(leaveType)) {
            return null;
        }
        try {
            return LeaveType.valueOf(leaveType.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("INVALID_LEAVE_TYPE", "不支持的请假类型：" + leaveType);
        }
    }

    private LeaveType parseRequiredLeaveType(String leaveType) {
        LeaveType parsed = parseLeaveType(leaveType);
        if (parsed == null) {
            throw new BusinessException("INVALID_LEAVE_TYPE", "请假类型不能为空");
        }
        return parsed;
    }

    private String currentBearerToken() {
        String authorization = servletRequest.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return null;
    }

    private record ToolCaller(
            Long userId,
            String username,
            String employeeName,
            UserRole role,
            String tenantId
    ) {
    }
}
