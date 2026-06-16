package com.example.hrai.ai.memory;

import com.example.hrai.entity.LeaveType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentMemoryServiceTest {

    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-14T04:00:00Z"), ZONE_ID);

    @Test
    void shouldIsolatePendingLeaveByUserAndSession() {
        AgentMemoryService service = new AgentMemoryService(new InMemoryAgentMemoryStore(CLOCK), Duration.ofMinutes(20), CLOCK);
        PendingLeaveApplyDTO pending = pendingLeave();

        PendingLeaveApplyDTO saved = service.savePendingLeave(1L, "session-a", pending);

        assertThat(service.getPendingLeave(1L, "session-a")).contains(saved);
        assertThat(service.getPendingLeave(1L, "session-b")).isEmpty();
        assertThat(service.getPendingLeave(2L, "session-a")).isEmpty();
    }

    @Test
    void shouldConfirmAndThenDeletePendingLeave() {
        AgentMemoryService service = new AgentMemoryService(new InMemoryAgentMemoryStore(CLOCK), Duration.ofMinutes(20), CLOCK);
        service.savePendingLeave(1L, "session-a", pendingLeave());

        PendingLeaveApplyDTO confirmed = service.confirmPendingLeave(1L, "session-a").orElseThrow();

        assertThat(confirmed.isConfirmed()).isTrue();
        service.deletePendingLeave(1L, "session-a");
        assertThat(service.getPendingLeave(1L, "session-a")).isEmpty();
    }

    @Test
    void shouldTreatExpiredPendingLeaveAsMissing() {
        MutableClock mutableClock = new MutableClock(CLOCK.instant(), ZONE_ID);
        AgentMemoryService service = new AgentMemoryService(
                new InMemoryAgentMemoryStore(mutableClock),
                Duration.ofMinutes(20),
                mutableClock
        );
        service.savePendingLeave(1L, "session-a", pendingLeave());

        mutableClock.advance(Duration.ofMinutes(21));

        assertThat(service.getPendingLeave(1L, "session-a")).isEmpty();
    }

    @Test
    void shouldGeneratePendingIdAndInitialVersion() {
        AgentMemoryService service = new AgentMemoryService(new InMemoryAgentMemoryStore(CLOCK), Duration.ofMinutes(20), CLOCK);

        PendingLeaveApplyDTO saved = service.savePendingLeave(1L, "session-a", pendingLeave());

        assertThat(saved.pendingId()).isNotBlank();
        assertThat(saved.version()).isEqualTo(1);
        assertThat(saved.status()).isEqualTo("PENDING_CONFIRMATION");
    }

    @Test
    void shouldRejectStaleVersionAndWrongPendingId() {
        AgentMemoryService service = new AgentMemoryService(new InMemoryAgentMemoryStore(CLOCK), Duration.ofMinutes(20), CLOCK);
        PendingLeaveApplyDTO saved = service.savePendingLeave(1L, "session-a", pendingLeave());

        assertThatThrownBy(() -> service.confirmPendingLeave(1L, "session-a", "wrong-id", 1))
                .isInstanceOf(com.example.hrai.exception.BusinessException.class)
                .extracting("code")
                .isEqualTo("PENDING_NOT_FOUND");
        assertThatThrownBy(() -> service.confirmPendingLeave(1L, "session-a", saved.pendingId(), 2))
                .isInstanceOf(com.example.hrai.exception.BusinessException.class)
                .extracting("code")
                .isEqualTo("PENDING_VERSION_CONFLICT");
    }

    @Test
    void shouldIncrementVersionWhenConfirmed() {
        AgentMemoryService service = new AgentMemoryService(new InMemoryAgentMemoryStore(CLOCK), Duration.ofMinutes(20), CLOCK);
        PendingLeaveApplyDTO saved = service.savePendingLeave(1L, "session-a", pendingLeave());

        PendingLeaveApplyDTO confirmed =
                service.confirmPendingLeave(1L, "session-a", saved.pendingId(), saved.version()).orElseThrow();

        assertThat(confirmed.version()).isEqualTo(2);
        assertThat(confirmed.status()).isEqualTo("CONFIRMED");
    }

    private PendingLeaveApplyDTO pendingLeave() {
        return new PendingLeaveApplyDTO(
                "pending_leave_apply",
                "张三",
                LeaveType.ANNUAL,
                LocalDateTime.of(2026, 6, 15, 14, 0),
                LocalDateTime.of(2026, 6, 15, 18, 0),
                "个人事务",
                false,
                LocalDateTime.now(CLOCK),
                LocalDateTime.now(CLOCK).plusMinutes(20)
        );
    }

    private static final class MutableClock extends Clock {

        private Instant instant;
        private final ZoneId zoneId;

        private MutableClock(Instant instant, ZoneId zoneId) {
            this.instant = instant;
            this.zoneId = zoneId;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return zoneId;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
