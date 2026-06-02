package com.example.hrai.ai.service;

import com.example.hrai.ai.dto.LeaveBalanceToolRequest;
import com.example.hrai.ai.dto.LeaveRecordsToolRequest;
import com.example.hrai.ai.security.ToolTokenContext;
import com.example.hrai.ai.security.ToolTokenService;
import com.example.hrai.dto.leave.LeaveApplicationResponse;
import com.example.hrai.dto.leave.LeaveBalanceResponse;
import com.example.hrai.entity.LeaveType;
import com.example.hrai.entity.UserRole;
import com.example.hrai.exception.BusinessException;
import com.example.hrai.security.AuthenticatedUser;
import com.example.hrai.security.CurrentUserService;
import com.example.hrai.service.ai.tools.LeaveTools;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AiToolService {

    private final CurrentUserService currentUserService;
    private final ToolTokenService toolTokenService;
    private final HttpServletRequest servletRequest;
    private final LeaveTools leaveTools;

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

    private ToolCaller currentToolCaller(String requiredScope, String requestToolToken) {
        String token = StringUtils.hasText(requestToolToken) ? requestToolToken.trim() : currentBearerToken();
        if (StringUtils.hasText(token)) {
            try {
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
            } catch (BusinessException exception) {
                if (!"INVALID_TOOL_TOKEN".equals(exception.getCode())) {
                    throw exception;
                }
            }
        }

        AuthenticatedUser user = currentUserService.currentUser();
        return new ToolCaller(user.userId(), user.username(), user.employeeName(), user.role(), "demo-tenant");
    }

    private String resolveEmployeeName(ToolCaller caller, String requestedEmployeeName) {
        String targetEmployeeName = StringUtils.hasText(requestedEmployeeName)
                ? requestedEmployeeName.trim()
                : caller.employeeName();
        if (caller.role() == UserRole.EMPLOYEE && !caller.employeeName().equals(targetEmployeeName)) {
            throw new BusinessException("FORBIDDEN", "员工只能查询或操作自己的请假信息");
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
