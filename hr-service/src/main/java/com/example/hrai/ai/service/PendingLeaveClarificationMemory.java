package com.example.hrai.ai.service;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 保存“请假信息还差一个槽位”的短期上下文。
 *
 * <p>它不是已创建的请假 pending，不参与确认/提交，只用于把用户下一轮
 * “年假/病假/事假”这类补充回答还原成完整请假请求。</p>
 */
@Component
class PendingLeaveClarificationMemory {

    private static final Duration TTL = Duration.ofMinutes(5);

    private final ConcurrentMap<String, Clarification> clarifications = new ConcurrentHashMap<>();
    private final Clock clock;

    PendingLeaveClarificationMemory(Clock agentClock) {
        this.clock = agentClock;
    }

    void save(Long userId, String sessionId, String originalMessage) {
        clarifications.put(key(userId, sessionId), new Clarification(originalMessage, clock.instant().plus(TTL)));
    }

    Optional<String> consume(Long userId, String sessionId) {
        String key = key(userId, sessionId);
        Clarification clarification = clarifications.remove(key);
        if (clarification == null || !clarification.expiresAt().isAfter(clock.instant())) {
            return Optional.empty();
        }
        return Optional.of(clarification.originalMessage());
    }

    private String key(Long userId, String sessionId) {
        return userId + ":" + sessionId;
    }

    private record Clarification(String originalMessage, Instant expiresAt) {
    }
}
