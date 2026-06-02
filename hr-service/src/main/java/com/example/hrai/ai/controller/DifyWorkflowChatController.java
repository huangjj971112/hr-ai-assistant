package com.example.hrai.ai.controller;

import com.example.hrai.ai.dto.DifyWorkflowChatRequest;
import com.example.hrai.ai.dto.DifyWorkflowChatResponse;
import com.example.hrai.ai.service.DifyWorkflowChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai/dify/workflow")
public class DifyWorkflowChatController {

    private final DifyWorkflowChatService difyWorkflowChatService;

    @PostMapping("/chat")
    public DifyWorkflowChatResponse chat(@Valid @RequestBody DifyWorkflowChatRequest request) {
        return difyWorkflowChatService.chat(request);
    }
}
