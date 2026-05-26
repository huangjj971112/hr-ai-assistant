package com.example.hrai.service;

import com.example.hrai.dto.knowledge.KnowledgeAskResponse;
import com.example.hrai.service.dify.DifyChatClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

    private final DifyChatClient difyChatClient;

    public KnowledgeAskResponse ask(String question) {
        return ask(question, "hr-admin");
    }

    public KnowledgeAskResponse ask(String question, String user) {
        return difyChatClient.ask(question, user);
    }

    public SseEmitter streamAsk(String question, String user) {
        return difyChatClient.streamAsk(question, user);
    }
}
