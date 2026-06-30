package com.example.hrai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.hrai.dto.leave.LeaveApplyRequest;
import com.example.hrai.entity.AiWorkflowRun;
import com.example.hrai.entity.AiWorkflowStepLog;
import com.example.hrai.entity.LeaveApplication;
import com.example.hrai.entity.LeaveStatus;
import com.example.hrai.entity.LeaveType;
import com.example.hrai.repository.AiWorkflowRunRepository;
import com.example.hrai.repository.AiWorkflowStepLogRepository;
import com.example.hrai.repository.EmployeeLeaveBalanceRepository;
import com.example.hrai.repository.LeaveApplicationRepository;
import com.example.hrai.service.LeaveService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class HrAiAssistantApplicationTests {

    private static final Pattern ACCESS_TOKEN_PATTERN = Pattern.compile("\"accessToken\":\"([^\"]+)\"");
    private static final Pattern WORKFLOW_ID_PATTERN = Pattern.compile("\"workflowId\":\"([^\"]+)\"");

    @LocalServerPort
    private int port;

    @Autowired
    private AiWorkflowRunRepository aiWorkflowRunRepository;

    @Autowired
    private AiWorkflowStepLogRepository aiWorkflowStepLogRepository;

    @Autowired
    private LeaveApplicationRepository leaveApplicationRepository;

    @Autowired
    private EmployeeLeaveBalanceRepository employeeLeaveBalanceRepository;

    private final TestRestTemplate restTemplate = new TestRestTemplate();

    @Test
    void contextLoads() {
    }

    @Test
    void shouldQueryLeaveBalance() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/hr/leave/balance?employeeName=张三"),
                HttpMethod.GET,
                authEntity(hrToken()),
                String.class
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"employeeName\":\"张三\"");
        assertThat(response.getBody()).contains("\"annualLeaveBalance\":5");
        assertThat(response.getBody()).contains("\"sickLeaveBalance\":3");
    }

    @Test
    void shouldApplyLeave() {
        String request = """
                {
                  "employeeName": "张三",
                  "leaveType": "ANNUAL",
                  "startTime": "2026-05-20 14:00:00",
                  "endTime": "2026-05-20 18:00:00",
                  "reason": "个人事务"
                }
                """;

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/hr/leave/apply"),
                HttpMethod.POST,
                authJsonEntity(request, hrToken()),
                String.class
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"status\":\"PENDING\"");
        assertThat(response.getBody()).contains("\"message\":\"请假申请已提交，等待审批\"");
    }

    @Test
    void shouldSearchCandidates() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/hr/candidates?keyword=Java"),
                HttpMethod.GET,
                authEntity(hrToken()),
                String.class
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("高级 Java 开发工程师");
    }

    @Test
    void shouldAllowHrCreatingUserAndNewUserCanLogin() {
        String request = """
                {
                  "username": "lisi_user",
                  "password": "123456",
                  "employeeName": "李四",
                  "role": "EMPLOYEE",
                  "enabled": true
                }
                """;

        ResponseEntity<String> createResponse = restTemplate.exchange(
                url("/api/hr/users"),
                HttpMethod.POST,
                authJsonEntity(request, hrToken()),
                String.class
        );

        assertThat(createResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(createResponse.getBody()).contains("\"username\":\"lisi_user\"");
        assertThat(createResponse.getBody()).contains("\"employeeName\":\"李四\"");
        assertThat(createResponse.getBody()).contains("\"role\":\"EMPLOYEE\"");
        assertThat(createResponse.getBody()).contains("\"employeeProfileStatus\":\"EXISTING\"");
        assertThat(createResponse.getBody()).contains("已绑定已有员工档案");

        String newUserToken = login("lisi_user", "123456");
        assertThat(newUserToken).isNotBlank();
    }

    @Test
    void shouldCreateZeroLeaveBalanceWhenCreatingNewEmployeeUser() {
        String request = """
                {
                  "username": "new_employee_user",
                  "password": "123456",
                  "employeeName": "测试员工A",
                  "role": "EMPLOYEE",
                  "enabled": true
                }
                """;

        ResponseEntity<String> createResponse = restTemplate.exchange(
                url("/api/hr/users"),
                HttpMethod.POST,
                authJsonEntity(request, hrToken()),
                String.class
        );

        assertThat(createResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(createResponse.getBody()).contains("\"employeeProfileStatus\":\"CREATED\"");
        assertThat(createResponse.getBody()).contains("已自动创建员工假期余额档案");

        ResponseEntity<String> balanceResponse = restTemplate.exchange(
                url("/api/employee/me/leave/balance"),
                HttpMethod.GET,
                authEntity(login("new_employee_user", "123456")),
                String.class
        );

        assertThat(balanceResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(balanceResponse.getBody()).contains("\"employeeName\":\"测试员工A\"");
        assertThat(balanceResponse.getBody()).contains("\"annualLeaveBalance\":0");
        assertThat(balanceResponse.getBody()).contains("\"sickLeaveBalance\":0");
    }

    @Test
    void shouldRejectBindingSameEmployeeNameToTwoEmployeeUsers() {
        String request = """
                {
                  "username": "duplicate_zhangsan",
                  "password": "123456",
                  "employeeName": "张三",
                  "role": "EMPLOYEE",
                  "enabled": true
                }
                """;

        ResponseEntity<String> createResponse = restTemplate.exchange(
                url("/api/hr/users"),
                HttpMethod.POST,
                authJsonEntity(request, hrToken()),
                String.class
        );

        assertThat(createResponse.getStatusCode().value()).isEqualTo(400);
        assertThat(createResponse.getBody()).contains("\"code\":\"EMPLOYEE_ACCOUNT_ALREADY_BOUND\"");
    }

    @Test
    void shouldRejectEmployeeManagingUsers() {
        String request = """
                {
                  "username": "wangwu_user",
                  "password": "123456",
                  "employeeName": "王五",
                  "role": "EMPLOYEE",
                  "enabled": true
                }
                """;

        ResponseEntity<String> createResponse = restTemplate.exchange(
                url("/api/hr/users"),
                HttpMethod.POST,
                authJsonEntity(request, employeeToken()),
                String.class
        );

        assertThat(createResponse.getStatusCode().value()).isEqualTo(400);
        assertThat(createResponse.getBody()).contains("\"code\":\"FORBIDDEN\"");
    }

    @Test
    void shouldScheduleInterview() {
        String request = """
                {
                  "candidateName": "李四",
                  "interviewerName": "王经理",
                  "interviewTime": "2026-05-21 15:00:00",
                  "round": "一面",
                  "mode": "线上"
                }
                """;

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/hr/interview/schedule"),
                HttpMethod.POST,
                authJsonEntity(request, hrToken()),
                String.class
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"status\":\"SCHEDULED\"");
        assertThat(response.getBody()).contains("\"message\":\"面试已安排\"");
    }

    @Test
    void shouldGenerateJd() {
        String request = """
                {
                  "positionName": "高级 Java 开发工程师",
                  "years": 5,
                  "skills": ["Spring Boot", "Spring Cloud", "MySQL", "Redis", "RAG"]
                }
                """;

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/hr/jd/generate"),
                HttpMethod.POST,
                authJsonEntity(request, hrToken()),
                String.class
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("岗位职责");
        assertThat(response.getBody()).contains("Spring Boot");
    }

    @Test
    void shouldChatWithRuleBasedAiEntry() {
        String request = """
                {
                  "message": "帮我查询张三的年假余额"
                }
                """;

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/ai/chat"),
                HttpMethod.POST,
                authJsonEntity(request, employeeToken()),
                String.class
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"intent\":\"LEAVE_BALANCE\"");
        assertThat(response.getBody()).contains("\"employeeName\":\"张三\"");
    }

    @Test
    void shouldRejectEmployeeQueryingOtherEmployeeLeaveByAiChat() {
        String request = """
                {
                  "message": "帮我查询李四的年假余额"
                }
                """;

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/ai/chat"),
                HttpMethod.POST,
                authJsonEntity(request, employeeToken()),
                String.class
        );

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).contains("\"code\":\"FORBIDDEN\"");
        assertThat(response.getBody()).contains("员工只能查询或操作自己的请假信息");
    }

    @Test
    void shouldRejectUnknownEmployeeNameByAiChat() {
        String request = """
                {
                  "message": "帮我查询三三的年假余额"
                }
                """;

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/ai/chat"),
                HttpMethod.POST,
                authJsonEntity(request, employeeToken()),
                String.class
        );

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).contains("\"code\":\"EMPLOYEE_NAME_NOT_RECOGNIZED\"");
        assertThat(response.getBody()).contains("未识别员工姓名：三三");
    }

    @Test
    void shouldAllowHrQueryingOtherEmployeeLeaveByAiChat() {
        String request = """
                {
                  "message": "帮我查询李四的年假余额"
                }
                """;

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/ai/chat"),
                HttpMethod.POST,
                authJsonEntity(request, hrToken()),
                String.class
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"intent\":\"LEAVE_BALANCE\"");
        assertThat(response.getBody()).contains("\"employeeName\":\"李四\"");
    }

    @Test
    void shouldQueryMyAnnualLeaveHistoryByAiChat() {
        String request = """
                {
                  "message": "我今年哪些天休了年假"
                }
                """;

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/ai/chat"),
                HttpMethod.POST,
                authJsonEntity(request, employeeToken()),
                String.class
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"intent\":\"LEAVE_HISTORY\"");
        assertThat(response.getBody()).contains("\"applyNo\":\"LV202601100001\"");
        assertThat(response.getBody()).contains("\"applyNo\":\"LV202603180001\"");
        assertThat(response.getBody()).doesNotContain("\"employeeName\":\"李四\"");
    }

    @Test
    void shouldRunLeaveApplyWorkflow() {
        String request = """
                {
                  "message": "我想明天下午请年假，原因是个人事务"
                }
                """;

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/ai/workflows/leave/apply"),
                HttpMethod.POST,
                authJsonEntity(request, employeeToken()),
                String.class
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"workflowId\":\"WF");
        assertThat(response.getBody()).contains("\"intent\":\"LEAVE_APPLY\"");
        assertThat(response.getBody()).contains("\"status\":\"SUCCESS\"");
        assertThat(response.getBody()).contains("\"stepName\":\"IntentRecognition\"");
        assertThat(response.getBody()).contains("\"stepName\":\"SlotExtraction\"");
        assertThat(response.getBody()).contains("\"stepName\":\"BalanceCheck\"");
        assertThat(response.getBody()).contains("\"stepName\":\"LeaveApply\"");
        assertThat(response.getBody()).contains("\"status\":\"PENDING_CONFIRMATION\"");
        assertThat(response.getBody()).contains("\"result\":null");

        Matcher matcher = WORKFLOW_ID_PATTERN.matcher(response.getBody());
        assertThat(matcher.find()).isTrue();
        String workflowId = matcher.group(1);
        assertThat(aiWorkflowRunRepository.selectCount(
                new LambdaQueryWrapper<AiWorkflowRun>()
                        .eq(AiWorkflowRun::getWorkflowId, workflowId)
                        .eq(AiWorkflowRun::getStatus, "SUCCESS")
        )).isEqualTo(1);
        assertThat(aiWorkflowStepLogRepository.selectCount(
                new LambdaQueryWrapper<AiWorkflowStepLog>()
                        .eq(AiWorkflowStepLog::getWorkflowId, workflowId)
        )).isGreaterThanOrEqualTo(5);
    }

    @Test
    void shouldLoginAndQueryCurrentEmployee() {
        String token = employeeToken();

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/employee/me"),
                HttpMethod.GET,
                authEntity(token),
                String.class
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"username\":\"zhangsan\"");
        assertThat(response.getBody()).contains("\"employeeName\":\"张三\"");
        assertThat(response.getBody()).contains("\"role\":\"EMPLOYEE\"");
    }

    @Test
    void shouldQueryMyLeaveBalanceFromAuthenticatedUser() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/employee/me/leave/balance"),
                HttpMethod.GET,
                authEntity(employeeToken()),
                String.class
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"employeeName\":\"张三\"");
        assertThat(response.getBody()).contains("\"annualLeaveBalance\":5");
    }

    @Test
    void shouldQueryMyAnnualLeaveApplicationsFromAuthenticatedUser() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/employee/me/leave/applications?year=2026&leaveType=ANNUAL"),
                HttpMethod.GET,
                authEntity(employeeToken()),
                String.class
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"employeeName\":\"张三\"");
        assertThat(response.getBody()).contains("\"leaveType\":\"ANNUAL\"");
        assertThat(response.getBody()).contains("\"applyNo\":\"LV202601100001\"");
        assertThat(response.getBody()).contains("\"applyNo\":\"LV202603180001\"");
        assertThat(response.getBody()).doesNotContain("\"employeeName\":\"李四\"");
    }

    @Test
    void shouldSubmitCurrentEmployeeLeaveFromConfirmationCard() {
        long beforeCount = leaveApplicationRepository.selectCount(null);
        String request = """
                {
                  "leaveType": "ANNUAL",
                  "startTime": "2026-06-11 14:00:00",
                  "endTime": "2026-06-11 18:00:00",
                  "reason": "个人事务"
                }
                """;

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/employee/me/leave/apply"),
                HttpMethod.POST,
                authJsonEntity(request, employeeToken()),
                String.class
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"status\":\"PENDING\"");
        assertThat(response.getBody()).contains("\"message\":\"请假申请已提交，等待审批\"");
        assertThat(response.getBody()).contains("\"applyNo\":\"LV");
        assertThat(leaveApplicationRepository.selectCount(null)).isEqualTo(beforeCount + 1);
    }

    @Test
    void shouldContinueApplyNumberFromDatabaseAfterServiceRestart() {
        LocalDateTime startTime = LocalDateTime.of(2027, 12, 31, 9, 0);
        LeaveApplication existingApplication = new LeaveApplication(
                null,
                "LV202712310007",
                "张三",
                LeaveType.ANNUAL,
                startTime.minusHours(1),
                startTime,
                "已有申请",
                LeaveStatus.PENDING,
                LocalDateTime.now()
        );
        leaveApplicationRepository.insert(existingApplication);

        LeaveApplyRequest request = new LeaveApplyRequest();
        request.setEmployeeName("张三");
        request.setLeaveType(LeaveType.ANNUAL);
        request.setStartTime(startTime);
        request.setEndTime(startTime.plusHours(8));
        request.setReason("验证服务重启后续号");

        LeaveService restartedLeaveService = new LeaveService(
                employeeLeaveBalanceRepository,
                leaveApplicationRepository
        );

        assertThat(restartedLeaveService.apply(request).getApplyNo()).isEqualTo("LV202712310008");
    }

    @Test
    void shouldAskEmployeeHandbookWithDifyFallback() {
        String request = """
                {
                  "question": "员工年假制度是什么？"
                }
                """;

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/employee/handbook/ask"),
                HttpMethod.POST,
                authJsonEntity(request, employeeToken()),
                String.class
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"source\":\"mock-employee-handbook\"");
        assertThat(response.getBody()).contains("员工手册智能问答尚未连接 Dify");
    }

    @Test
    void shouldStreamEmployeeHandbookWithDifyFallback() {
        String request = """
                {
                  "question": "员工年假制度是什么？"
                }
                """;

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/employee/handbook/ask/stream"),
                HttpMethod.POST,
                authJsonEntity(request, employeeToken()),
                String.class
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("event:meta");
        assertThat(response.getBody()).contains("event:answer");
        assertThat(response.getBody()).contains("event:done");
        assertThat(response.getBody()).contains("mock-employee-handbook");
    }

    @Test
    void shouldCallDifyWorkflowChatWithMockFallback() {
        String request = """
                {
                  "message": "帮我查一下我的年假余额"
                }
                """;

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/ai/dify/workflow/chat"),
                HttpMethod.POST,
                authJsonEntity(request, employeeToken()),
                String.class
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"source\":\"mock-dify-workflow\"");
        assertThat(response.getBody()).contains("帮我查一下我的年假余额");
    }

    @Test
    void shouldCallDifyHrAgentFromExistingChatEndpoint() {
        String request = """
                {
                  "message": "帮我查这周考勤和年假余额",
                  "agentType": "HR_AGENT"
                }
                """;

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/ai/dify/workflow/chat"),
                HttpMethod.POST,
                authJsonEntity(request, employeeToken()),
                String.class
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"source\":\"mock-dify-agent\"");
        assertThat(response.getBody()).contains("帮我查这周考勤和年假余额");
        assertThat(response.getBody()).doesNotContain("toolToken");
    }

    @Test
    void shouldCallLeaveBalanceToolForCurrentEmployee() {
        String request = """
                {
                  "employeeName": "张三"
                }
                """;

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/ai/tools/leave/balance"),
                HttpMethod.POST,
                authJsonEntity(request, employeeToken()),
                String.class
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"employeeName\":\"张三\"");
        assertThat(response.getBody()).contains("\"annualLeaveBalance\":5");
    }

    @Test
    void shouldAllowHrCallingLeaveBalanceToolForOtherEmployee() {
        String request = """
                {
                  "employeeName": "李四"
                }
                """;

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/ai/tools/leave/balance"),
                HttpMethod.POST,
                authJsonEntity(request, hrToken()),
                String.class
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"employeeName\":\"李四\"");
        assertThat(response.getBody()).contains("\"annualLeaveBalance\":8");
    }

    @Test
    void shouldRejectEmployeeCallingLeaveBalanceToolForOtherEmployee() {
        String request = """
                {
                  "employeeName": "李四"
                }
                """;

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/ai/tools/leave/balance"),
                HttpMethod.POST,
                authJsonEntity(request, employeeToken()),
                String.class
        );

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).contains("\"code\":\"FORBIDDEN\"");
        assertThat(response.getBody()).contains("员工只能查询或操作自己的信息");
    }

    @Test
    void shouldCallLeaveRecordsToolForCurrentEmployee() {
        String request = """
                {
                  "employeeName": "张三",
                  "year": 2026,
                  "leaveType": "ANNUAL"
                }
                """;

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/ai/tools/leave/records"),
                HttpMethod.POST,
                authJsonEntity(request, employeeToken()),
                String.class
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"applyNo\":\"LV202601100001\"");
        assertThat(response.getBody()).contains("\"applyNo\":\"LV202603180001\"");
        assertThat(response.getBody()).doesNotContain("\"employeeName\":\"李四\"");
    }

    @Test
    void shouldQueryCurrentEmployeeAttendance() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/employee/me/attendance/records?startDate=2026-06-03&endDate=2026-06-04"),
                HttpMethod.GET,
                authEntity(employeeToken()),
                String.class
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"employeeName\":\"张三\"");
        assertThat(response.getBody()).contains("\"attendanceDate\":\"2026-06-03\"");
        assertThat(response.getBody()).contains("\"attendanceDate\":\"2026-06-04\"");
        assertThat(response.getBody()).contains("\"status\":\"LATE\"");
        assertThat(response.getBody()).contains("迟到 18 分钟");
    }

    @Test
    void shouldCallAttendanceRecordsToolForCurrentEmployee() {
        String request = """
                {
                  "employeeName": "张三",
                  "startDate": "2026-06-03",
                  "endDate": "2026-06-05"
                }
                """;

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/ai/tools/attendance/records"),
                HttpMethod.POST,
                authJsonEntity(request, employeeToken()),
                String.class
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"employeeName\":\"张三\"");
        assertThat(response.getBody()).contains("\"attendanceDate\":\"2026-06-03\"");
        assertThat(response.getBody()).contains("\"attendanceDate\":\"2026-06-04\"");
        assertThat(response.getBody()).contains("\"status\":\"EARLY_LEAVE\"");
        assertThat(response.getBody()).doesNotContain("\"employeeName\":\"李四\"");
    }

    @Test
    void shouldDefaultMissingAttendanceEndDateToStartDate() {
        String request = """
                {
                  "employeeName": "张三",
                  "startDate": "2026-06-04"
                }
                """;

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/ai/tools/attendance/records"),
                HttpMethod.POST,
                authJsonEntity(request, employeeToken()),
                String.class
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"attendanceDate\":\"2026-06-04\"");
        assertThat(response.getBody()).doesNotContain("\"attendanceDate\":\"2026-06-03\"");
        assertThat(response.getBody()).doesNotContain("\"attendanceDate\":\"2026-06-05\"");
    }

    @Test
    void shouldRejectInvalidAttendanceDateRange() {
        String request = """
                {
                  "employeeName": "张三",
                  "startDate": "2026-06-05",
                  "endDate": "2026-06-03"
                }
                """;

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/ai/tools/attendance/records"),
                HttpMethod.POST,
                authJsonEntity(request, employeeToken()),
                String.class
        );

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).contains("\"code\":\"INVALID_ATTENDANCE_DATE_RANGE\"");
    }

    @Test
    void shouldRejectEmployeeCallingAttendanceToolForOtherEmployee() {
        String request = """
                {
                  "employeeName": "李四",
                  "attendanceDate": "2026-06-05"
                }
                """;

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/ai/tools/attendance/records"),
                HttpMethod.POST,
                authJsonEntity(request, employeeToken()),
                String.class
        );

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).contains("\"code\":\"FORBIDDEN\"");
        assertThat(response.getBody()).contains("员工只能查询或操作自己的信息");
    }

    @Test
    void shouldPreviewLeaveApplyWithoutWritingDatabase() {
        long beforeCount = leaveApplicationRepository.selectCount(null);
        String request = """
                {
                  "employeeName": "张三",
                  "leaveType": "ANNUAL",
                  "startTime": "2026-06-08 14:00:00",
                  "endTime": "2026-06-08 18:00:00",
                  "reason": "个人事务",
                  "confirmed": false
                }
                """;

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/ai/tools/leave/apply"),
                HttpMethod.POST,
                authJsonEntity(request, employeeToken()),
                String.class
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"submitted\":false");
        assertThat(response.getBody()).contains("\"status\":\"DRAFT\"");
        assertThat(leaveApplicationRepository.selectCount(null)).isEqualTo(beforeCount);
    }

    @Test
    void shouldRejectAiToolLeaveSubmissionEvenWhenConfirmed() {
        long beforeCount = leaveApplicationRepository.selectCount(null);
        String request = """
                {
                  "employeeName": "张三",
                  "leaveType": "ANNUAL",
                  "startTime": "2026-06-09 14:00:00",
                  "endTime": "2026-06-09 18:00:00",
                  "reason": "个人事务",
                  "confirmed": true
                }
                """;

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/ai/tools/leave/apply"),
                HttpMethod.POST,
                authJsonEntity(request, employeeToken()),
                String.class
        );

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).contains("\"code\":\"AI_TOOL_CONFIRMATION_NOT_ALLOWED\"");
        assertThat(leaveApplicationRepository.selectCount(null)).isEqualTo(beforeCount);
    }

    @Test
    void shouldRejectEmployeeApplyingLeaveForOtherEmployee() {
        String request = """
                {
                  "employeeName": "李四",
                  "leaveType": "ANNUAL",
                  "startTime": "2026-06-10 09:00:00",
                  "endTime": "2026-06-10 18:00:00",
                  "reason": "个人事务",
                  "confirmed": true
                }
                """;

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/ai/tools/leave/apply"),
                HttpMethod.POST,
                authJsonEntity(request, employeeToken()),
                String.class
        );

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).contains("\"code\":\"FORBIDDEN\"");
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpEntity<String> jsonEntity(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private HttpEntity<String> authJsonEntity(String body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }

    private HttpEntity<Void> authEntity(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    private String employeeToken() {
        return login("zhangsan", "123456");
    }

    private String hrToken() {
        return login("hr_admin", "123456");
    }

    private String login(String username, String password) {
        String request = """
                {
                  "username": "%s",
                  "password": "%s"
                }
                """.formatted(username, password);
        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/api/auth/login"),
                jsonEntity(request),
                String.class
        );
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        Matcher matcher = ACCESS_TOKEN_PATTERN.matcher(response.getBody());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}
