package com.example.hrai.controller;

import com.example.hrai.dto.interview.InterviewScheduleRequest;
import com.example.hrai.dto.interview.InterviewScheduleResponse;
import com.example.hrai.service.InterviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/hr/interview")
public class InterviewController {

    private final InterviewService interviewService;

    @PostMapping("/schedule")
    public InterviewScheduleResponse schedule(@Valid @RequestBody InterviewScheduleRequest request) {
        return interviewService.schedule(request);
    }
}
