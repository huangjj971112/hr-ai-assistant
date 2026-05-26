package com.example.hrai.service.ai.tools;

import com.example.hrai.dto.candidate.CandidateResponse;
import com.example.hrai.service.CandidateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CandidateTools {

    private final CandidateService candidateService;

    public List<CandidateResponse> searchCandidates(String keyword) {
        return candidateService.search(keyword);
    }
}
