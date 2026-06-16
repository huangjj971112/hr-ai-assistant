package com.example.hrai.service.ai;

import com.example.hrai.ai.tool.HrAgentTools;
import com.example.hrai.security.AuthenticatedUser;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@ConditionalOnBean(ChatClient.Builder.class)
public class SpringAiToolCallingService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ChatClient chatClient;
    private final String systemPrompt;
    private final Clock agentClock;

    public SpringAiToolCallingService(
            ChatClient.Builder chatClientBuilder,
            HrAgentTools hrAgentTools,
            Clock agentClock,
            @Value("classpath:prompts/hr-agent-system.txt") Resource systemPromptResource
    ) throws IOException {
        this.chatClient = chatClientBuilder.defaultTools(hrAgentTools).build();
        this.systemPrompt = systemPromptResource.getContentAsString(StandardCharsets.UTF_8);
        this.agentClock = agentClock;
    }

    public String chat(String message, AuthenticatedUser user, String sessionId) {
        String runtimeContext = """

                当前登录员工：%s
                当前 userId：%s
                当前 sessionId：%s
                当前时间：%s
                """.formatted(
                user.employeeName(),
                user.userId(),
                sessionId,
                LocalDateTime.now(agentClock).format(DATE_TIME_FORMATTER)
        );
        return chatClient.prompt()
                .system(systemPrompt + runtimeContext)
                .user(message)
                .call()
                .content();
    }
}
