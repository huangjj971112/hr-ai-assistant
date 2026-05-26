package com.example.hrai.workflow.leave;

import com.example.hrai.entity.LeaveType;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Order(20)
@Component
public class SlotExtractionStep implements LeaveApplyWorkflowStep {

    @Override
    public void execute(LeaveApplyWorkflowContext context) {
        context.setLeaveType(extractLeaveType(context.getMessage()));
        LocalDate date = extractDate(context.getMessage());
        if (context.getMessage().contains("上午")) {
            context.setStartTime(date.atTime(9, 0));
            context.setEndTime(date.atTime(12, 0));
        } else if (context.getMessage().contains("下午")) {
            context.setStartTime(date.atTime(14, 0));
            context.setEndTime(date.atTime(18, 0));
        } else {
            context.setStartTime(date.atTime(9, 0));
            context.setEndTime(date.atTime(18, 0));
        }
        context.setReason(extractReason(context.getMessage()));
        context.addStep("SlotExtraction", "SUCCESS", "提取请假类型、开始时间、结束时间、原因");
    }

    private LeaveType extractLeaveType(String message) {
        if (message.contains("病假")) {
            return LeaveType.SICK;
        }
        if (message.contains("事假")) {
            return LeaveType.PERSONAL;
        }
        return LeaveType.ANNUAL;
    }

    private LocalDate extractDate(String message) {
        if (message.contains("后天")) {
            return LocalDate.now().plusDays(2);
        }
        if (message.contains("今天")) {
            return LocalDate.now();
        }
        return LocalDate.now().plusDays(1);
    }

    private String extractReason(String message) {
        int reasonIndex = message.indexOf("原因");
        if (reasonIndex >= 0) {
            String reason = message.substring(reasonIndex)
                    .replace("原因是", "")
                    .replace("原因：", "")
                    .replace("原因", "")
                    .trim();
            if (!reason.isBlank()) {
                return reason;
            }
        }
        if (message.contains("个人事务")) {
            return "个人事务";
        }
        return "AI Workflow 模拟提交";
    }
}
