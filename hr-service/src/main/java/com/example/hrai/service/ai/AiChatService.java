package com.example.hrai.service.ai;

import com.example.hrai.dto.ai.AiChatResponse;
import com.example.hrai.dto.leave.LeaveApplicationResponse;
import com.example.hrai.entity.LeaveType;
import com.example.hrai.entity.UserRole;
import com.example.hrai.exception.BusinessException;
import com.example.hrai.security.AuthenticatedUser;
import com.example.hrai.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AiChatService {

    private static final List<String> KNOWN_EMPLOYEES = List.of("张三", "李四", "王五");
    private static final Pattern EXPLICIT_EMPLOYEE_NAME_PATTERN = Pattern.compile("帮我查询(.+?)的|帮我查(.+?)的|查询(.+?)的|查看(.+?)的");

    private final RuleBasedIntentRecognizer intentRecognizer;
    private final HrToolService hrToolService;
    private final CurrentUserService currentUserService;

    public AiChatResponse chat(String message) {
        AuthenticatedUser currentUser = currentUserService.currentUser();
        HrIntent intent = intentRecognizer.recognize(message);
        return switch (intent) {
            case LEAVE_BALANCE -> {
                String employeeName = resolveLeaveEmployeeName(message, currentUser);
                Object data = hrToolService.queryLeaveBalance(employeeName);
                yield new AiChatResponse(intent.name(), "已查询到 " + employeeName + " 的假期余额。", data);
            }
            case LEAVE_HISTORY -> {
                String employeeName = resolveLeaveEmployeeName(message, currentUser);
                Integer year = extractYear(message);
                LeaveType leaveType = extractLeaveType(message);
                List<LeaveApplicationResponse> data = hrToolService.queryLeaveApplications(employeeName, year, leaveType);
                yield new AiChatResponse(
                        intent.name(),
                        buildLeaveHistoryReply(employeeName, year, leaveType, data),
                        data
                );
            }
            case LEAVE_APPLY -> {
                String employeeName = resolveLeaveEmployeeName(message, currentUser);
                Object data = hrToolService.applyLeave(employeeName);
                yield new AiChatResponse(intent.name(), "已按模拟规则提交请假申请。", data);
            }
            case CANDIDATE_SEARCH -> {
                requireHr(currentUser);
                Object data = hrToolService.searchCandidates(extractCandidateKeyword(message));
                yield new AiChatResponse(intent.name(), "已为你查询候选人。", data);
            }
            case INTERVIEW_SCHEDULE -> {
                requireHr(currentUser);
                Object data = hrToolService.scheduleInterview();
                yield new AiChatResponse(intent.name(), "已按模拟规则安排面试。", data);
            }
            case JD_GENERATE -> {
                requireHr(currentUser);
                Object data = hrToolService.generateJd();
                yield new AiChatResponse(intent.name(), "已生成一份 JD 草稿。", data);
            }
            case KNOWLEDGE_QA -> {
                Object data = hrToolService.askKnowledge(message);
                yield new AiChatResponse(intent.name(), "知识库问答能力已预留，当前返回模拟答案。", data);
            }
            case UNKNOWN -> new AiChatResponse(intent.name(), "暂未识别该 HR 意图，请尝试描述年假余额、请假、候选人、面试或 JD 需求。", null);
        };
    }

    private String resolveLeaveEmployeeName(String message, AuthenticatedUser currentUser) {
        String requestedEmployeeName = extractEmployeeName(message);
        validateExplicitEmployeeNameIfPresent(message, requestedEmployeeName);
        if (currentUser.role() == UserRole.EMPLOYEE) {
            String targetEmployeeName = requestedEmployeeName == null ? currentUser.employeeName() : requestedEmployeeName;
            if (!currentUser.employeeName().equals(targetEmployeeName)) {
                throw new BusinessException("FORBIDDEN", "员工只能查询或操作自己的请假信息");
            }
            return targetEmployeeName;
        }
        return requestedEmployeeName == null ? "张三" : requestedEmployeeName;
    }

    private String extractEmployeeName(String message) {
        return KNOWN_EMPLOYEES.stream()
                .filter(message::contains)
                .findFirst()
                .orElse(null);
    }

    private void validateExplicitEmployeeNameIfPresent(String message, String requestedEmployeeName) {
        if (requestedEmployeeName != null) {
            return;
        }
        Matcher matcher = EXPLICIT_EMPLOYEE_NAME_PATTERN.matcher(message);
        if (matcher.find()) {
            String possibleName = firstNonBlank(matcher.group(1), matcher.group(2), matcher.group(3));
            if (possibleName != null && !possibleName.isBlank() && !"我".equals(possibleName) && !"自己".equals(possibleName)) {
                throw new BusinessException("EMPLOYEE_NAME_NOT_RECOGNIZED", "未识别员工姓名：" + possibleName);
            }
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private void requireHr(AuthenticatedUser currentUser) {
        if (currentUser.role() != UserRole.HR) {
            throw new BusinessException("FORBIDDEN", "该 AI 工具仅 HR 角色可使用");
        }
    }

    private Integer extractYear(String message) {
        if (message.contains("今年")) {
            return LocalDateTime.now().getYear();
        }
        Matcher matcher = Pattern.compile("(20\\d{2})").matcher(message);
        if (matcher.find()) {
            return Integer.valueOf(matcher.group(1));
        }
        return null;
    }

    private LeaveType extractLeaveType(String message) {
        if (message.contains("病假")) {
            return LeaveType.SICK;
        }
        if (message.contains("事假")) {
            return LeaveType.PERSONAL;
        }
        if (message.contains("年假")) {
            return LeaveType.ANNUAL;
        }
        return null;
    }

    private String buildLeaveHistoryReply(
            String employeeName,
            Integer year,
            LeaveType leaveType,
            List<LeaveApplicationResponse> applications
    ) {
        String yearText = year == null ? "" : year + " 年";
        String leaveTypeText = leaveType == null ? "请假" : leaveTypeName(leaveType);
        if (applications.isEmpty()) {
            return employeeName + " 暂无" + yearText + leaveTypeText + "记录。";
        }
        return "已查询到 " + employeeName + " 的" + yearText + leaveTypeText + "记录，共 " + applications.size() + " 条。";
    }

    private String leaveTypeName(LeaveType leaveType) {
        return switch (leaveType) {
            case ANNUAL -> "年假";
            case SICK -> "病假";
            case PERSONAL -> "事假";
        };
    }

    private String extractCandidateKeyword(String message) {
        if (message.contains("Java")) {
            return "Java";
        }
        if (message.contains("AI")) {
            return "AI";
        }
        if (message.contains("产品")) {
            return "产品";
        }
        return "";
    }
}
