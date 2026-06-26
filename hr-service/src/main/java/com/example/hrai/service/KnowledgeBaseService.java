package com.example.hrai.service;

import com.example.hrai.dto.knowledge.KnowledgeAskResponse;
import com.example.hrai.service.dify.DifyChatClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

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

    public KnowledgeAskResponse ask(String question, String user, Map<String, Object> extraInputs) {
        return difyChatClient.ask(question, user, extraInputs);
    }

    public SseEmitter streamAsk(String question, String user) {
        return difyChatClient.streamAsk(question, user);
    }
}
