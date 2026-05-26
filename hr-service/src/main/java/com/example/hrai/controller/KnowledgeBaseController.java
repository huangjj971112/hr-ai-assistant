package com.example.hrai.controller;

import com.example.hrai.dto.knowledge.KnowledgeAskRequest;
import com.example.hrai.dto.knowledge.KnowledgeAskResponse;
import com.example.hrai.service.KnowledgeBaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/hr/knowledge")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    @PostMapping("/ask")
    public KnowledgeAskResponse ask(@Valid @RequestBody KnowledgeAskRequest request) {
        return knowledgeBaseService.ask(request.getQuestion());
    }
}
