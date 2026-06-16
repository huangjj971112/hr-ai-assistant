package com.example.hrai.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DifyWorkflowChatResponse {

    private String answer;

    private String source;

    private Boolean needConfirm;

    private String leaveType;

    private String startTime;

    private String endTime;

    private String reason;

    private String message;
}
