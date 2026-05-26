package com.example.hrai.dto.candidate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CandidateResponse {

    private String name;
    private String position;
    private int years;
    private List<String> skills;
    private String status;
    private String phone;
    private String email;
}
