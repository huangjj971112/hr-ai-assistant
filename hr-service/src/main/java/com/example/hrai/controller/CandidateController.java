package com.example.hrai.controller;

import com.example.hrai.dto.candidate.CandidateResponse;
import com.example.hrai.service.CandidateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/hr/candidates")
public class CandidateController {

    private final CandidateService candidateService;

    @GetMapping
    public List<CandidateResponse> search(@RequestParam(required = false, defaultValue = "") String keyword) {
        return candidateService.search(keyword);
    }
}
