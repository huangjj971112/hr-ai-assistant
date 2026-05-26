package com.example.hrai.workflow.leave;

import com.example.hrai.dto.leave.LeaveApplyRequest;
import com.example.hrai.dto.leave.LeaveApplyResponse;
import com.example.hrai.service.LeaveService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(50)
@Component
@RequiredArgsConstructor
public class LeaveApplySubmitStep implements LeaveApplyWorkflowStep {

    private final LeaveService leaveService;

    @Override
    public void execute(LeaveApplyWorkflowContext context) {
        LeaveApplyRequest request = new LeaveApplyRequest();
        request.setEmployeeName(context.getEmployeeName());
        request.setLeaveType(context.getLeaveType());
        request.setStartTime(context.getStartTime());
        request.setEndTime(context.getEndTime());
        request.setReason(context.getReason());

        LeaveApplyResponse response = leaveService.apply(request);
        context.setResult(response);
        context.addStep("LeaveApply", "SUCCESS", "请假申请已提交");
    }
}
