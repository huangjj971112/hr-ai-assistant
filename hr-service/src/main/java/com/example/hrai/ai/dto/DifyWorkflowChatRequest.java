package com.example.hrai.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DifyWorkflowChatRequest {

    @NotBlank
    private String message;
}
