package com.example.hrai.workflow.leave;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(60)
@Component
public class ResponseBuildStep implements LeaveApplyWorkflowStep {

    @Override
    public void execute(LeaveApplyWorkflowContext context) {
        context.setStatus("SUCCESS");
        context.addStep("ResponseBuild", "SUCCESS", "构建 Workflow 响应");
    }
}
