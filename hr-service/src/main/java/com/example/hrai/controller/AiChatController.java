package com.example.hrai.controller;

import com.example.hrai.dto.ai.AiChatRequest;
import com.example.hrai.dto.ai.AiChatResponse;
import com.example.hrai.service.ai.AiChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai")
public class AiChatController {

    private final AiChatService aiChatService;

    @PostMapping("/chat")
    public AiChatResponse chat(@Valid @RequestBody AiChatRequest request) {
        return aiChatService.chat(request.getMessage());
    }
}
