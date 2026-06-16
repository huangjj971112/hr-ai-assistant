package com.example.hrai.service.ai.tools;

import com.example.hrai.dto.leave.LeaveApplyRequest;
import com.example.hrai.dto.leave.LeaveApplyResponse;
import com.example.hrai.dto.leave.LeaveApplicationResponse;
import com.example.hrai.dto.leave.LeaveBalanceResponse;
import com.example.hrai.entity.LeaveType;
import com.example.hrai.service.LeaveService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LeaveTools {

    private final LeaveService leaveService;

    public LeaveBalanceResponse queryLeaveBalance(String employeeName) {
        return leaveService.getBalance(employeeName);
    }

    public List<LeaveApplicationResponse> queryLeaveApplications(String employeeName, Integer year, LeaveType leaveType) {
        return leaveService.listApplications(employeeName, year, leaveType);
    }

    public LeaveApplyResponse applyLeave(String employeeName) {
        LeaveApplyRequest request = new LeaveApplyRequest();
        request.setEmployeeName(employeeName);
        request.setLeaveType(LeaveType.ANNUAL);
        request.setStartTime(LocalDateTime.now().plusDays(1).withHour(14).withMinute(0).withSecond(0).withNano(0));
        request.setEndTime(LocalDateTime.now().plusDays(1).withHour(18).withMinute(0).withSecond(0).withNano(0));
        request.setReason("AI 对话入口模拟提交");
        return leaveService.apply(request);
    }

    public LeaveApplyResponse applyLeave(LeaveApplyRequest request) {
        return leaveService.apply(request);
    }

    public void validateLeaveApply(LeaveApplyRequest request) {
        leaveService.validateApply(request);
    }
}
