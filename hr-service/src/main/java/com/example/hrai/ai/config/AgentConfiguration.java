package com.example.hrai.ai.config;

import com.example.hrai.ai.memory.AgentMemoryService;
import com.example.hrai.ai.memory.AgentMemoryStore;
import com.example.hrai.ai.memory.InMemoryAgentMemoryStore;
import com.example.hrai.service.ai.LeaveRequestParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;

@Configuration
public class AgentConfiguration {

    @Bean
    public Clock agentClock() {
        return Clock.system(ZoneId.of("Asia/Shanghai"));
    }

    @Bean
    @ConditionalOnProperty(name = "app.ai.agent-memory.store", havingValue = "in-memory")
    public AgentMemoryStore agentMemoryStore(Clock agentClock) {
        return new InMemoryAgentMemoryStore(agentClock);
    }

    @Bean
    public AgentMemoryService agentMemoryService(
            AgentMemoryStore agentMemoryStore,
            Clock agentClock,
            @Value("${app.ai.agent-memory.pending-leave-ttl:20m}") Duration pendingLeaveTtl
    ) {
        return new AgentMemoryService(agentMemoryStore, pendingLeaveTtl, agentClock);
    }

    @Bean
    public LeaveRequestParser leaveRequestParser(Clock agentClock) {
        return new LeaveRequestParser(agentClock);
    }

    @Bean
    @ConditionalOnBean(ChatModel.class)
    public ChatClient.Builder agentChatClientBuilder(ChatModel chatModel) {
        return ChatClient.builder(chatModel);
    }
}
