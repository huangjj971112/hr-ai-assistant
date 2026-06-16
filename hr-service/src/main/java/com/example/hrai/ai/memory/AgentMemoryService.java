package com.example.hrai.ai.memory;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import com.example.hrai.exception.BusinessException;

public class AgentMemoryService {

    private static final String PENDING_LEAVE_KEY_PREFIX = "agent:pending_leave_apply:";

    private final AgentMemoryStore memoryStore;
    private final Duration pendingLeaveTtl;
    private final Clock clock;

    public AgentMemoryService(AgentMemoryStore memoryStore, Duration pendingLeaveTtl, Clock clock) {
        this.memoryStore = memoryStore;
        this.pendingLeaveTtl = pendingLeaveTtl;
        this.clock = clock;
    }

    public PendingLeaveApplyDTO savePendingLeave(Long userId, String sessionId, PendingLeaveApplyDTO pendingLeave) {
        LocalDateTime now = LocalDateTime.now(clock);
        // pendingId/version/status 由服务端生成，不能信任模型或前端传入的状态字段。
        PendingLeaveApplyDTO stored = new PendingLeaveApplyDTO(
                "pending_leave_apply",
                pendingLeave.employeeName(),
                pendingLeave.leaveType(),
                pendingLeave.startTime(),
                pendingLeave.endTime(),
                pendingLeave.reason(),
                false,
                now,
                now.plus(pendingLeaveTtl),
                UUID.randomUUID().toString(),
                1,
                "PENDING_CONFIRMATION",
                null,
                null
        );
        memoryStore.save(key(userId, sessionId), stored, pendingLeaveTtl);
        return stored;
    }

    public Optional<PendingLeaveApplyDTO> getPendingLeave(Long userId, String sessionId) {
        return memoryStore.get(key(userId, sessionId))
                .filter(pending -> pending.expiresAt().isAfter(LocalDateTime.now(clock)));
    }

    public Optional<PendingLeaveApplyDTO> confirmPendingLeave(Long userId, String sessionId) {
        return getPendingLeave(userId, sessionId).map(pending -> {
            PendingLeaveApplyDTO confirmed = pending.withConfirmed();
            Duration remainingTtl = Duration.between(LocalDateTime.now(clock), confirmed.expiresAt());
            memoryStore.save(key(userId, sessionId), confirmed, remainingTtl);
            return confirmed;
        });
    }

    public Optional<PendingLeaveApplyDTO> confirmPendingLeave(
            Long userId,
            String sessionId,
            String pendingId,
            int expectedVersion
    ) {
        return getPendingLeave(userId, sessionId).map(pending -> {
            // pendingId 约束申请身份，version 防止并发确认或使用过期确认卡片。
            if (!pending.pendingId().equals(pendingId)) {
                throw new BusinessException("PENDING_NOT_FOUND", "待确认申请不存在");
            }
            if (pending.version() != expectedVersion) {
                throw new BusinessException("PENDING_VERSION_CONFLICT", "待确认申请版本已变化");
            }
            PendingLeaveApplyDTO confirmed = pending.withConfirmed();
            saveWithRemainingTtl(userId, sessionId, confirmed);
            return confirmed;
        });
    }

    public PendingLeaveApplyDTO savePendingState(Long userId, String sessionId, PendingLeaveApplyDTO pending) {
        saveWithRemainingTtl(userId, sessionId, pending);
        return pending;
    }

    public void deletePendingLeave(Long userId, String sessionId) {
        memoryStore.delete(key(userId, sessionId));
    }

    static String key(Long userId, String sessionId) {
        return PENDING_LEAVE_KEY_PREFIX + userId + ":" + sessionId;
    }

    private void saveWithRemainingTtl(Long userId, String sessionId, PendingLeaveApplyDTO pending) {
        Duration remainingTtl = Duration.between(LocalDateTime.now(clock), pending.expiresAt());
        memoryStore.save(key(userId, sessionId), pending, remainingTtl);
    }
}
