package com.example.hrai.ai.dify;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DifyWorkflowResponse {

    private String answer;

    private String source;

    private Map<String, Object> raw;
}
