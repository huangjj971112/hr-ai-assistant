package com.example.hrai.ai.memory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryAgentMemoryStore implements AgentMemoryStore {

    private final ConcurrentMap<String, StoredPendingLeave> pendingLeaves = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryAgentMemoryStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void save(String key, PendingLeaveApplyDTO pendingLeave, Duration ttl) {
        pendingLeaves.put(key, new StoredPendingLeave(pendingLeave, clock.instant().plus(ttl)));
    }

    @Override
    public Optional<PendingLeaveApplyDTO> get(String key) {
        StoredPendingLeave stored = pendingLeaves.get(key);
        if (stored == null) {
            return Optional.empty();
        }
        if (!stored.expiresAt().isAfter(clock.instant())) {
            pendingLeaves.remove(key);
            return Optional.empty();
        }
        return Optional.of(stored.pendingLeave());
    }

    @Override
    public void delete(String key) {
        pendingLeaves.remove(key);
    }

    private record StoredPendingLeave(PendingLeaveApplyDTO pendingLeave, Instant expiresAt) {
    }
}
