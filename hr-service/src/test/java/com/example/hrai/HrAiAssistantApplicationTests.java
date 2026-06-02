package com.example.hrai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.hrai.entity.AiWorkflowRun;
import com.example.hrai.entity.AiWorkflowStepLog;
import com.example.hrai.repository.AiWorkflowRunRepository;
import com.example.hrai.repository.AiWorkflowStepLogRepository;
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
        assertThat(response.getBody()).contains("\"message\":\"请假申请已提交\"");
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
        assertThat(response.getBody()).contains("\"applyNo\":\"LV");

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
        assertThat(response.getBody()).contains("员工只能查询或操作自己的请假信息");
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
