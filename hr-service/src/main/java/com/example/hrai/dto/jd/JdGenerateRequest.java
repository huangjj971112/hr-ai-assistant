package com.example.hrai.dto.jd;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class JdGenerateRequest {

    @NotBlank
    private String positionName;

    @Min(0)
    private int years;

    @NotEmpty
    private List<String> skills;
}
