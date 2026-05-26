package com.example.hrai.workflow.leave;

import com.example.hrai.dto.leave.LeaveBalanceResponse;
import com.example.hrai.entity.LeaveType;
import com.example.hrai.service.LeaveService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Order(40)
@Component
@RequiredArgsConstructor
public class LeaveBalanceCheckStep implements LeaveApplyWorkflowStep {

    private final LeaveService leaveService;

    @Override
    public void execute(LeaveApplyWorkflowContext context) {
        LeaveBalanceResponse balance = leaveService.getBalance(context.getEmployeeName());
        context.setLeaveBalance(balance);

        double requestedDays = calculateRequestedDays(context);
        int availableDays = context.getLeaveType() == LeaveType.SICK
                ? balance.getSickLeaveBalance()
                : balance.getAnnualLeaveBalance();
        if (context.getLeaveType() != LeaveType.PERSONAL && availableDays < requestedDays) {
            context.addStep("BalanceCheck", "FAILED", "假期余额不足，当前余额：" + availableDays + " 天");
            return;
        }
        context.addStep("BalanceCheck", "SUCCESS", "假期余额充足，申请时长约 " + requestedDays + " 天");
    }

    private double calculateRequestedDays(LeaveApplyWorkflowContext context) {
        long hours = Duration.between(context.getStartTime(), context.getEndTime()).toHours();
        if (hours <= 4) {
            return 0.5;
        }
        return 1.0;
    }
}
