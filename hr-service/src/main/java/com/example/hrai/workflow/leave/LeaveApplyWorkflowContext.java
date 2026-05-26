package com.example.hrai.workflow.leave;

import com.example.hrai.dto.leave.LeaveApplyResponse;
import com.example.hrai.dto.leave.LeaveBalanceResponse;
import com.example.hrai.dto.workflow.WorkflowStepResponse;
import com.example.hrai.entity.LeaveType;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class LeaveApplyWorkflowContext {

    private String workflowId;
    private LocalDateTime startedAt;
    private String employeeName;
    private String message;
    private String intent;
    private LeaveType leaveType;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String reason;
    private LeaveBalanceResponse leaveBalance;
    private LeaveApplyResponse result;
    private boolean stopped;
    private String status = "RUNNING";
    private final List<WorkflowStepResponse> steps = new ArrayList<>();

    public void addStep(String stepName, String status, String message) {
        steps.add(new WorkflowStepResponse(stepName, status, message, LocalDateTime.now()));
        if ("FAILED".equals(status)) {
            this.status = "FAILED";
            this.stopped = true;
        }
    }
}
