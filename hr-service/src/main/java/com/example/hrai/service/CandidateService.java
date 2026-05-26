package com.example.hrai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.hrai.dto.candidate.CandidateResponse;
import com.example.hrai.entity.Candidate;
import com.example.hrai.repository.CandidateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CandidateService {

    private final CandidateRepository candidateRepository;

    public List<CandidateResponse> search(String keyword) {
        return candidateRepository.selectList(buildSearchWrapper(keyword)).stream()
                .map(candidate -> new CandidateResponse(
                        candidate.getName(),
                        candidate.getPosition(),
                        candidate.getYears(),
                        splitSkills(candidate.getSkills()),
                        candidate.getStatus(),
                        candidate.getPhone(),
                        candidate.getEmail()
                ))
                .toList();
    }

    private LambdaQueryWrapper<Candidate> buildSearchWrapper(String keyword) {
        LambdaQueryWrapper<Candidate> wrapper = new LambdaQueryWrapper<>();
        if (keyword == null || keyword.isBlank()) {
            return wrapper;
        }
        wrapper.like(Candidate::getName, keyword)
                .or()
                .like(Candidate::getPosition, keyword)
                .or()
                .like(Candidate::getSkills, keyword);
        return wrapper;
    }

    private List<String> splitSkills(String skills) {
        if (skills == null || skills.isBlank()) {
            return List.of();
        }
        return Arrays.stream(skills.split(","))
                .map(String::trim)
                .filter(skill -> !skill.isBlank())
                .toList();
    }
}
