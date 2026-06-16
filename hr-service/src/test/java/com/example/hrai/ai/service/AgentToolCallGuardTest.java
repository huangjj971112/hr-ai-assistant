package com.example.hrai.ai.service;

import com.example.hrai.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentToolCallGuardTest {

    @Test
    void shouldRejectRepeatedToolCallWithinSameModelRequest() {
        AgentToolCallGuard guard = new AgentToolCallGuard(4);

        guard.check("create_leave_pending");

        assertThatThrownBy(() -> guard.check("create_leave_pending"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("MCP_REPEATED_TOOL_CALL");
    }

    @Test
    void shouldRejectCallsBeyondRequestBudget() {
        AgentToolCallGuard guard = new AgentToolCallGuard(2);

        guard.check("query_leave_balance");
        guard.check("create_leave_pending");

        assertThatThrownBy(() -> guard.check("query_leave_policy"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("MCP_TOOL_CALL_LIMIT_EXCEEDED");
    }

    @Test
    void shouldExposeAcceptedCallCount() {
        AgentToolCallGuard guard = new AgentToolCallGuard(4);

        guard.check("query_leave_balance");
        guard.check("query_leave_policy");

        assertThat(guard.callCount()).isEqualTo(2);
    }
}
