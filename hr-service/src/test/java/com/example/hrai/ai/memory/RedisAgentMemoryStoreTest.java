package com.example.hrai.ai.memory;

import com.example.hrai.entity.LeaveType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisAgentMemoryStoreTest {

    @Test
    void shouldSerializePendingLeaveWithTtlAndReadItBack() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        RedisAgentMemoryStore store = new RedisAgentMemoryStore(redisTemplate, objectMapper);
        PendingLeaveApplyDTO pending = new PendingLeaveApplyDTO(
                "pending_leave_apply",
                "张三",
                LeaveType.ANNUAL,
                LocalDateTime.of(2026, 6, 15, 14, 0),
                LocalDateTime.of(2026, 6, 15, 18, 0),
                "个人事务",
                false,
                LocalDateTime.of(2026, 6, 14, 12, 0),
                LocalDateTime.of(2026, 6, 14, 12, 20)
        );
        Duration ttl = Duration.ofMinutes(20);

        store.save("pending-key", pending, ttl);
        when(valueOperations.get("pending-key")).thenReturn(objectMapper.writeValueAsString(pending));

        verify(valueOperations).set("pending-key", objectMapper.writeValueAsString(pending), ttl);
        assertThat(store.get("pending-key")).contains(pending);
    }
}
