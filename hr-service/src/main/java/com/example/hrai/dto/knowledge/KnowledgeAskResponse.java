package com.example.hrai.dto.knowledge;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeAskResponse {

    private String answer;
    private String source;
    private String conversationId;
}
