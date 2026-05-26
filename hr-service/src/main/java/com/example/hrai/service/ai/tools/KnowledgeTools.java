package com.example.hrai.service.ai.tools;

import com.example.hrai.dto.knowledge.KnowledgeAskResponse;
import com.example.hrai.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KnowledgeTools {

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeAskResponse askKnowledge(String question) {
        return knowledgeBaseService.ask(question);
    }
}
