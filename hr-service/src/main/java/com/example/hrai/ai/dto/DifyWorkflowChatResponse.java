package com.example.hrai.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DifyWorkflowChatResponse {

    private String answer;

    private String source;

    private Map<String, Object> raw;
}
