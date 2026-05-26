package com.example.hrai.workflow.leave;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(10)
@Component
public class IntentRecognitionStep implements LeaveApplyWorkflowStep {

    @Override
    public void execute(LeaveApplyWorkflowContext context) {
        if (!context.getMessage().contains("请假")
                && !context.getMessage().contains("年假")
                && !context.getMessage().contains("休假")) {
            context.addStep("IntentRecognition", "FAILED", "未识别到请假申请意图");
            return;
        }
        context.setIntent("LEAVE_APPLY");
        context.addStep("IntentRecognition", "SUCCESS", "识别到请假申请意图");
    }
}
