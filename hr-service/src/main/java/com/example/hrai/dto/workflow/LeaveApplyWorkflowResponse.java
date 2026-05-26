package com.example.hrai.dto.workflow;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeaveApplyWorkflowResponse {

    private String workflowId;
    private String intent;
    private String status;
    private List<WorkflowStepResponse> steps;
    private Object result;
}
