package com.example.hrai.dto.workflow;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowStepResponse {

    private String stepName;
    private String status;
    private String message;
    private LocalDateTime executedAt;
}
