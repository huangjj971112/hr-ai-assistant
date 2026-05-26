package com.example.hrai.service.ai;

import com.example.hrai.dto.candidate.CandidateResponse;
import com.example.hrai.dto.interview.InterviewScheduleResponse;
import com.example.hrai.dto.jd.JdGenerateResponse;
import com.example.hrai.dto.knowledge.KnowledgeAskResponse;
import com.example.hrai.dto.leave.LeaveApplyResponse;
import com.example.hrai.dto.leave.LeaveApplicationResponse;
import com.example.hrai.dto.leave.LeaveBalanceResponse;
import com.example.hrai.entity.LeaveType;
import com.example.hrai.service.ai.tools.CandidateTools;
import com.example.hrai.service.ai.tools.InterviewTools;
import com.example.hrai.service.ai.tools.JdTools;
import com.example.hrai.service.ai.tools.KnowledgeTools;
import com.example.hrai.service.ai.tools.LeaveTools;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HrToolService {

    private final LeaveTools leaveTools;
    private final CandidateTools candidateTools;
    private final InterviewTools interviewTools;
    private final JdTools jdTools;
    private final KnowledgeTools knowledgeTools;

    public LeaveBalanceResponse queryLeaveBalance(String employeeName) {
        return leaveTools.queryLeaveBalance(employeeName);
    }

    public List<LeaveApplicationResponse> queryLeaveApplications(String employeeName, Integer year, LeaveType leaveType) {
        return leaveTools.queryLeaveApplications(employeeName, year, leaveType);
    }

    public LeaveApplyResponse applyLeave(String employeeName) {
        return leaveTools.applyLeave(employeeName);
    }

    public List<CandidateResponse> searchCandidates(String keyword) {
        return candidateTools.searchCandidates(keyword);
    }

    public InterviewScheduleResponse scheduleInterview() {
        return interviewTools.scheduleInterview();
    }

    public JdGenerateResponse generateJd() {
        return jdTools.generateJd();
    }

    public KnowledgeAskResponse askKnowledge(String question) {
        return knowledgeTools.askKnowledge(question);
    }
}
