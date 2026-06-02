package com.example.hrai.ai.dify;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DifyWorkflowRequest {

    private Map<String, Object> inputs;

    private String responseMode;

    private String user;
}
