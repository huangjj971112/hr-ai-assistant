package com.example.hrai.workflow.leave;

import com.example.hrai.service.LeaveService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(30)
@Component
@RequiredArgsConstructor
public class EmployeeValidateStep implements LeaveApplyWorkflowStep {

    private final LeaveService leaveService;

    @Override
    public void execute(LeaveApplyWorkflowContext context) {
        if (!leaveService.employeeExists(context.getEmployeeName())) {
            context.addStep("EmployeeValidate", "FAILED", "员工不存在：" + context.getEmployeeName());
            return;
        }
        context.addStep("EmployeeValidate", "SUCCESS", "员工存在：" + context.getEmployeeName());
    }
}
