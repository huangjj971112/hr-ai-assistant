package com.example.hrai.controller;

import com.example.hrai.dto.knowledge.KnowledgeAskRequest;
import com.example.hrai.dto.knowledge.KnowledgeAskResponse;
import com.example.hrai.security.AuthenticatedUser;
import com.example.hrai.security.CurrentUserService;
import com.example.hrai.service.KnowledgeBaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/employee/handbook")
public class EmployeeHandbookController {

    private final CurrentUserService currentUserService;
    private final KnowledgeBaseService knowledgeBaseService;

    @PostMapping("/ask")
    public KnowledgeAskResponse ask(@Valid @RequestBody KnowledgeAskRequest request) {
        AuthenticatedUser user = currentUserService.currentUser();
        return knowledgeBaseService.ask(request.getQuestion(), user.username());
    }

    @PostMapping("/ask/stream")
    public SseEmitter streamAsk(@Valid @RequestBody KnowledgeAskRequest request) {
        AuthenticatedUser user = currentUserService.currentUser();
        return knowledgeBaseService.streamAsk(request.getQuestion(), user.username());
    }
}
