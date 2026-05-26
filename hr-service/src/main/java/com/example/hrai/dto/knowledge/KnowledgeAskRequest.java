package com.example.hrai.dto.knowledge;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class KnowledgeAskRequest {

    @NotBlank
    private String question;
}
