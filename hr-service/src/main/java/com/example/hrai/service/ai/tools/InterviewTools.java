package com.example.hrai.service.ai.tools;

import com.example.hrai.dto.interview.InterviewScheduleRequest;
import com.example.hrai.dto.interview.InterviewScheduleResponse;
import com.example.hrai.service.InterviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class InterviewTools {

    private final InterviewService interviewService;

    public InterviewScheduleResponse scheduleInterview() {
        InterviewScheduleRequest request = new InterviewScheduleRequest();
        request.setCandidateName("李四");
        request.setInterviewerName("王经理");
        request.setInterviewTime(LocalDateTime.now().plusDays(2).withHour(15).withMinute(0).withSecond(0).withNano(0));
        request.setRound("一面");
        request.setMode("线上");
        return interviewService.schedule(request);
    }
}
