package com.example.hrai.ai.memory;

import java.time.Duration;
import java.util.Optional;

public interface AgentMemoryStore {

    void save(String key, PendingLeaveApplyDTO pendingLeave, Duration ttl);

    Optional<PendingLeaveApplyDTO> get(String key);

    void delete(String key);
}
