package com.example.hrai.ai.service;

import com.example.hrai.ai.dto.LeaveBalanceToolRequest;
import com.example.hrai.ai.dto.LeaveRecordsToolRequest;
import com.example.hrai.dto.leave.LeaveApplicationResponse;
import com.example.hrai.dto.leave.LeaveBalanceResponse;
import com.example.hrai.entity.LeaveType;
import com.example.hrai.entity.UserRole;
import com.example.hrai.exception.BusinessException;
import com.example.hrai.security.AuthenticatedUser;
import com.example.hrai.security.CurrentUserService;
import com.example.hrai.service.ai.tools.LeaveTools;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AiToolService {

    private final CurrentUserService currentUserService;
    private final LeaveTools leaveTools;

    public LeaveBalanceResponse queryLeaveBalance(LeaveBalanceToolRequest request) {
        AuthenticatedUser user = currentUserService.currentUser();
        String employeeName = resolveEmployeeName(user, request.getEmployeeName());
        return leaveTools.queryLeaveBalance(employeeName);
    }

    public List<LeaveApplicationResponse> queryLeaveRecords(LeaveRecordsToolRequest request) {
        AuthenticatedUser user = currentUserService.currentUser();
        String employeeName = resolveEmployeeName(user, request.getEmployeeName());
        LeaveType leaveType = parseLeaveType(request.getLeaveType());
        return leaveTools.queryLeaveApplications(employeeName, request.getYear(), leaveType);
    }

    private String resolveEmployeeName(AuthenticatedUser user, String requestedEmployeeName) {
        String targetEmployeeName = StringUtils.hasText(requestedEmployeeName)
                ? requestedEmployeeName.trim()
                : user.employeeName();
        if (user.role() == UserRole.EMPLOYEE && !user.employeeName().equals(targetEmployeeName)) {
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
}
