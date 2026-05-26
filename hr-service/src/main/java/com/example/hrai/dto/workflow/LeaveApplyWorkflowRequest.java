package com.example.hrai.dto.workflow;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LeaveApplyWorkflowRequest {

    private String employeeName;

    @NotBlank
    private String message;
}
