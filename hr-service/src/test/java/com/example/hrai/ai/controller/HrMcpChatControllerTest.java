package com.example.hrai.ai.controller;

import com.example.hrai.ai.service.HrMcpChatService;
import com.example.hrai.dto.ai.AiChatRequest;
import com.example.hrai.dto.ai.AiChatResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HrMcpChatControllerTest {

    @Test
    void shouldDelegateAuthenticatedChatRequestToMcpChatService() {
        HrMcpChatService service = mock(HrMcpChatService.class);
        HrMcpChatController controller = new HrMcpChatController(service);
        AiChatRequest request = new AiChatRequest();
        request.setMessage("查询我的年假余额");
        request.setSessionId("frontend-session");
        when(service.chat(request.getMessage(), request.getSessionId()))
                .thenReturn(new AiChatResponse("LEAVE_BALANCE", "假期余额查询成功", null));

        AiChatResponse response = controller.chat(request);

        assertThat(response.getIntent()).isEqualTo("LEAVE_BALANCE");
        verify(service).chat("查询我的年假余额", "frontend-session");
    }
}
