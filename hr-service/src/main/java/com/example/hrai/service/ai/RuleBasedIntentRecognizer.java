package com.example.hrai.service.ai;

import org.springframework.stereotype.Component;

@Component
public class RuleBasedIntentRecognizer {

    public HrIntent recognize(String message) {
        if (message == null || message.isBlank()) {
            return HrIntent.UNKNOWN;
        }
        if (message.contains("年假") && message.contains("余额")) {
            return HrIntent.LEAVE_BALANCE;
        }
        if ((message.contains("休") || message.contains("请假") || message.contains("年假") || message.contains("病假"))
                && (message.contains("哪些天") || message.contains("记录") || message.contains("休过")
                || message.contains("请过") || message.contains("历史"))) {
            return HrIntent.LEAVE_HISTORY;
        }
        if (message.contains("请假")) {
            return HrIntent.LEAVE_APPLY;
        }
        if (message.contains("候选人")) {
            return HrIntent.CANDIDATE_SEARCH;
        }
        if (message.contains("面试")) {
            return HrIntent.INTERVIEW_SCHEDULE;
        }
        if (message.contains("JD") || message.contains("招聘需求")) {
            return HrIntent.JD_GENERATE;
        }
        if (message.contains("制度") || message.contains("政策") || message.contains("知识库")) {
            return HrIntent.KNOWLEDGE_QA;
        }
        return HrIntent.UNKNOWN;
    }
}
