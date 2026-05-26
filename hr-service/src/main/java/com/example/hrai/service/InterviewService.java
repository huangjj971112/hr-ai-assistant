package com.example.hrai.service;

import com.example.hrai.dto.interview.InterviewScheduleRequest;
import com.example.hrai.dto.interview.InterviewScheduleResponse;
import com.example.hrai.entity.InterviewSchedule;
import com.example.hrai.entity.InterviewStatus;
import com.example.hrai.repository.InterviewScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class InterviewService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final InterviewScheduleRepository interviewScheduleRepository;
    private final ConcurrentMap<LocalDate, AtomicInteger> dailyInterviewSequences = new ConcurrentHashMap<>();

    public InterviewScheduleResponse schedule(InterviewScheduleRequest request) {
        InterviewSchedule schedule = new InterviewSchedule();
        schedule.setInterviewNo(generateInterviewNo(request.getInterviewTime().toLocalDate()));
        schedule.setCandidateName(request.getCandidateName());
        schedule.setInterviewerName(request.getInterviewerName());
        schedule.setInterviewTime(request.getInterviewTime());
        schedule.setRound(request.getRound());
        schedule.setMode(request.getMode());
        schedule.setStatus(InterviewStatus.SCHEDULED);
        schedule.setCreatedAt(LocalDateTime.now());
        interviewScheduleRepository.insert(schedule);

        return new InterviewScheduleResponse(schedule.getInterviewNo(), schedule.getStatus().name(), "面试已安排");
    }

    private String generateInterviewNo(LocalDate date) {
        int sequence = dailyInterviewSequences
                .computeIfAbsent(date, ignored -> new AtomicInteger(0))
                .incrementAndGet();
        return "IV" + date.format(DATE_FORMATTER) + String.format("%04d", sequence);
    }
}
