package com.example.hrai.ai;

import com.example.hrai.repository.LeaveApplicationRepository;
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

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AgentConversationIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private LeaveApplicationRepository leaveApplicationRepository;

    @Autowired
    private Clock agentClock;

    private final TestRestTemplate restTemplate = new TestRestTemplate();

    @Test
    void shouldCreatePendingWithoutSubmitting() {
        long before = leaveApplicationRepository.selectCount(null);

        String response = chat("pending-session", "帮我请明天下午年假");

        assertThat(response).contains("\"intent\":\"LEAVE_APPLY_PENDING\"");
        assertThat(response).contains("已为你生成请假申请");
        String tomorrowAfternoon = LocalDate.now(agentClock)
                .plusDays(1)
                .atTime(14, 0)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        assertThat(response).contains(tomorrowAfternoon);
        assertThat(leaveApplicationRepository.selectCount(null)).isEqualTo(before);
    }

    @Test
    void shouldSubmitPendingAfterConfirmation() {
        long before = leaveApplicationRepository.selectCount(null);
        chat("confirm-session", "帮我请明天下午年假");

        String response = chat("confirm-session", "确认");

        assertThat(response).contains("\"intent\":\"LEAVE_APPLY_SUBMITTED\"");
        assertThat(response).contains("请假申请已提交");
        assertThat(leaveApplicationRepository.selectCount(null)).isEqualTo(before + 1);
        assertThat(chat("confirm-session", "确认")).contains("当前没有待确认的申请");
    }

    @Test
    void shouldCancelPending() {
        chat("cancel-session", "帮我请明天下午年假");

        assertThat(chat("cancel-session", "取消")).contains("已取消待确认的请假申请");
        assertThat(chat("cancel-session", "确认")).contains("当前没有待确认的申请");
    }

    @Test
    void shouldReturnMissingPendingWhenConfirmingAnotherSession() {
        chat("original-session", "帮我请明天下午年假");

        assertThat(chat("other-session", "确定")).contains("当前没有待确认的申请");
    }

    @Test
    void shouldDirectlyQueryAnnualLeaveBalance() {
        String response = chat("balance-session", "查询我的年假余额");

        assertThat(response).contains("\"intent\":\"LEAVE_BALANCE\"");
        assertThat(response).contains("\"annualLeaveBalance\":5");
    }

    @Test
    void shouldDirectlyQueryCurrentMonthAttendance() {
        String response = chat("attendance-session", "查询本月考勤");

        assertThat(response).contains("\"intent\":\"ATTENDANCE_QUERY\"");
        assertThat(response).contains("\"employeeName\":\"张三\"");
    }

    @Test
    void shouldQueryBalanceAndPolicyWithoutSubmitting() {
        long before = leaveApplicationRepository.selectCount(null);

        String response = chat("enough-session", "我下周想请三天年假，帮我看看够不够");

        assertThat(response).contains("\"intent\":\"LEAVE_ADVICE\"");
        assertThat(response).contains("queryLeaveBalance");
        assertThat(response).contains("queryLeavePolicy");
        assertThat(leaveApplicationRepository.selectCount(null)).isEqualTo(before);
    }

    @Test
    void shouldStillCreatePendingWhenUserSaysSubmitWithoutConfirmation() {
        long before = leaveApplicationRepository.selectCount(null);

        String response = chat("direct-submit-session", "帮我提交明天下午年假");

        assertThat(response).contains("\"intent\":\"LEAVE_APPLY_PENDING\"");
        assertThat(response).contains("请回复‘确认’后提交");
        assertThat(leaveApplicationRepository.selectCount(null)).isEqualTo(before);
    }

    @Test
    void shouldNotAllowLegacyWorkflowToBypassConfirmation() {
        long before = leaveApplicationRepository.selectCount(null);
        String request = """
                {
                  "message": "我想明天下午请年假，原因是个人事务"
                }
                """;
        HttpHeaders headers = employeeHeaders();

        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/ai/workflows/leave/apply",
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                String.class
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("PENDING_CONFIRMATION");
        assertThat(leaveApplicationRepository.selectCount(null)).isEqualTo(before);
    }

    private String chat(String sessionId, String message) {
        String request = """
                {
                  "sessionId": "%s",
                  "message": "%s"
                }
                """.formatted(sessionId, message);
        HttpHeaders headers = employeeHeaders();

        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/ai/chat",
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                String.class
        );
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        return response.getBody();
    }

    private HttpHeaders employeeHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", "1");
        headers.set("X-Username", "zhangsan");
        headers.set("X-Employee-Name", "%E5%BC%A0%E4%B8%89");
        headers.set("X-User-Role", "EMPLOYEE");
        return headers;
    }
}
