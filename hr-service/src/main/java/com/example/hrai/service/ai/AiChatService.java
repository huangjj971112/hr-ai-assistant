package com.example.hrai.service.ai;

import com.example.hrai.dto.ai.AiChatResponse;
import com.example.hrai.ai.dto.AttendanceAgentToolRequest;
import com.example.hrai.ai.dto.ConfirmedLeaveApplyToolRequest;
import com.example.hrai.ai.dto.LeaveBalanceAgentToolRequest;
import com.example.hrai.ai.dto.LeavePolicyAgentToolRequest;
import com.example.hrai.ai.memory.AgentMemoryService;
import com.example.hrai.ai.memory.PendingLeaveApplyDTO;
import com.example.hrai.ai.tool.HrAgentTools;
import com.example.hrai.ai.tool.ToolResult;
import com.example.hrai.dto.leave.LeaveApplicationResponse;
import com.example.hrai.entity.LeaveType;
import com.example.hrai.entity.UserRole;
import com.example.hrai.exception.BusinessException;
import com.example.hrai.security.AuthenticatedUser;
import com.example.hrai.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AiChatService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> CONFIRM_WORDS = Set.of("确认", "确定", "提交");
    private static final Set<String> CANCEL_WORDS = Set.of("取消", "算了", "不提交");
    private static final List<String> KNOWN_EMPLOYEES = List.of("张三", "李四", "王五");
    private static final Pattern EXPLICIT_EMPLOYEE_NAME_PATTERN = Pattern.compile("帮我查询(.+?)的|帮我查(.+?)的|查询(.+?)的|查看(.+?)的");

    private final RuleBasedIntentRecognizer intentRecognizer;
    private final HrToolService hrToolService;
    private final CurrentUserService currentUserService;
    private final AgentMemoryService agentMemoryService;
    private final LeaveRequestParser leaveRequestParser;
    private final HrAgentTools hrAgentTools;
    private final Clock agentClock;
    private final ObjectProvider<SpringAiToolCallingService> springAiToolCallingServiceProvider;

    public AiChatResponse chat(String message) {
        return chat(message, "legacy-session");
    }

    public AiChatResponse chat(String message, String sessionId) {
        String resolvedSessionId = sessionId == null || sessionId.isBlank() ? "default-session" : sessionId.trim();
        AuthenticatedUser currentUser = currentUserService.currentUser();
        String normalizedMessage = message == null ? "" : message.trim();
        if (CONFIRM_WORDS.contains(normalizedMessage)) {
            return confirmPendingLeave(currentUser, resolvedSessionId);
        }
        if (CANCEL_WORDS.contains(normalizedMessage)) {
            return cancelPendingLeave(currentUser, resolvedSessionId);
        }
        if (isLeaveApplyRequest(message)) {
            return createPendingLeave(currentUser, resolvedSessionId, message);
        }

        SpringAiToolCallingService springAiToolCallingService = springAiToolCallingServiceProvider.getIfAvailable();
        if (springAiToolCallingService != null) {
            try {
                String reply = springAiToolCallingService.chat(message, currentUser, resolvedSessionId);
                return new AiChatResponse("SPRING_AI_TOOL_CALLING", reply, null);
            } catch (RuntimeException ignored) {
                // Fall through to the deterministic local path when the configured model is unavailable.
            }
        }
        if (isLeaveAdvice(message)) {
            return queryLeaveAdvice();
        }
        if (isAttendanceQuery(message)) {
            return queryAttendance();
        }
        if (isLeavePolicyQuery(message)) {
            ToolResult<?> result = hrAgentTools.queryLeavePolicy(new LeavePolicyAgentToolRequest(message));
            return new AiChatResponse("LEAVE_POLICY", result.message(), result);
        }

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
                yield createPendingLeave(currentUser, resolvedSessionId, message);
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

    private AiChatResponse createPendingLeave(AuthenticatedUser currentUser, String sessionId, String message) {
        ParsedLeaveRequest parsed = leaveRequestParser.parse(message);
        if (!parsed.complete()) {
            return new AiChatResponse(
                    "LEAVE_APPLY_MISSING_PARAMS",
                    "请补充" + String.join("、", parsed.missingFields()) + "。",
                    parsed
            );
        }
        PendingLeaveApplyDTO pending = agentMemoryService.savePendingLeave(
                currentUser.userId(),
                sessionId,
                new PendingLeaveApplyDTO(
                        "pending_leave_apply",
                        currentUser.employeeName(),
                        parsed.leaveType(),
                        parsed.startTime(),
                        parsed.endTime(),
                        parsed.reason(),
                        false,
                        null,
                        null
                )
        );
        String reply = "已为你生成请假申请：" + leaveTypeName(pending.leaveType())
                + "，" + pending.startTime().format(DATE_TIME_FORMATTER)
                + " 到 " + pending.endTime().format(DATE_TIME_FORMATTER)
                + "，请回复‘确认’后提交。";
        return new AiChatResponse("LEAVE_APPLY_PENDING", reply, pending);
    }

    private AiChatResponse confirmPendingLeave(AuthenticatedUser currentUser, String sessionId) {
        try {
            return agentMemoryService.confirmPendingLeave(currentUser.userId(), sessionId)
                    .map(ignored -> {
                        ToolResult<?> result = hrAgentTools.applyLeave(new ConfirmedLeaveApplyToolRequest(sessionId, true));
                        String intent = result.success() ? "LEAVE_APPLY_SUBMITTED" : "LEAVE_APPLY_FAILED";
                        return new AiChatResponse(intent, result.message(), result);
                    })
                    .orElseGet(() -> new AiChatResponse("NO_PENDING_LEAVE_APPLY", "当前没有待确认的申请", null));
        } catch (RuntimeException exception) {
            return new AiChatResponse("LEAVE_APPLY_FAILED", "提交请假申请失败，请稍后重试。", null);
        }
    }

    private AiChatResponse cancelPendingLeave(AuthenticatedUser currentUser, String sessionId) {
        if (agentMemoryService.getPendingLeave(currentUser.userId(), sessionId).isEmpty()) {
            return new AiChatResponse("NO_PENDING_LEAVE_APPLY", "当前没有待确认的申请", null);
        }
        agentMemoryService.deletePendingLeave(currentUser.userId(), sessionId);
        return new AiChatResponse("LEAVE_APPLY_CANCELLED", "已取消待确认的请假申请", null);
    }

    private AiChatResponse queryAttendance() {
        LocalDate today = LocalDate.now(agentClock);
        ToolResult<?> result = hrAgentTools.queryAttendance(
                new AttendanceAgentToolRequest(null, today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth()))
        );
        return new AiChatResponse("ATTENDANCE_QUERY", result.message(), result);
    }

    private AiChatResponse queryLeaveAdvice() {
        Map<String, Object> results = new LinkedHashMap<>();
        results.put("queryLeaveBalance", hrAgentTools.queryLeaveBalance(new LeaveBalanceAgentToolRequest(null)));
        results.put("queryLeavePolicy", hrAgentTools.queryLeavePolicy(new LeavePolicyAgentToolRequest("年假申请和余额制度")));
        return new AiChatResponse("LEAVE_ADVICE", "已查询假期余额和请假制度，未提交申请。", results);
    }

    private boolean isLeaveApplyRequest(String message) {
        if (message == null || isLeaveAdvice(message) || isLeaveQuery(message)) {
            return false;
        }
        boolean mentionsLeave = message.contains("请假")
                || message.contains("年假")
                || message.contains("病假")
                || message.contains("事假");
        boolean requestsAction = message.contains("帮我请")
                || message.contains("想请")
                || message.contains("我要请")
                || message.contains("申请")
                || message.contains("提交")
                || message.contains("办理")
                || message.contains("请假");
        return mentionsLeave && requestsAction;
    }

    private boolean isLeaveQuery(String message) {
        return message.contains("查询")
                || message.contains("查看")
                || message.contains("余额")
                || message.contains("制度")
                || message.contains("政策")
                || message.contains("记录")
                || message.contains("历史")
                || message.contains("请过")
                || message.contains("休过");
    }

    private boolean isLeaveAdvice(String message) {
        return message != null && message.contains("够不够");
    }

    private boolean isAttendanceQuery(String message) {
        return message != null && message.contains("考勤");
    }

    private boolean isLeavePolicyQuery(String message) {
        return message != null && (message.contains("请假制度") || message.contains("请假政策"));
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
