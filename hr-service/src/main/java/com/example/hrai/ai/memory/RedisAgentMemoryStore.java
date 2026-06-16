package com.example.hrai.ai.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "app.ai.agent-memory.store", havingValue = "redis", matchIfMissing = true)
public class RedisAgentMemoryStore implements AgentMemoryStore {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisAgentMemoryStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(String key, PendingLeaveApplyDTO pendingLeave, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(pendingLeave), ttl);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化待确认请假申请", exception);
        }
    }

    @Override
    public Optional<PendingLeaveApplyDTO> get(String key) {
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(value, PendingLeaveApplyDTO.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法读取待确认请假申请", exception);
        }
    }

    @Override
    public void delete(String key) {
        redisTemplate.delete(key);
    }
}
