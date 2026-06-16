package com.example.hrai.service.ai;

import com.example.hrai.entity.LeaveType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class LeaveRequestParserTest {

    private final LeaveRequestParser parser = new LeaveRequestParser(
            Clock.fixed(Instant.parse("2026-06-14T04:00:00Z"), ZoneId.of("Asia/Shanghai"))
    );

    @Test
    void shouldParseTomorrowAfternoonAnnualLeave() {
        ParsedLeaveRequest parsed = parser.parse("帮我请明天下午年假");

        assertThat(parsed.leaveType()).isEqualTo(LeaveType.ANNUAL);
        assertThat(parsed.startTime()).isEqualTo(LocalDateTime.of(2026, 6, 15, 14, 0));
        assertThat(parsed.endTime()).isEqualTo(LocalDateTime.of(2026, 6, 15, 18, 0));
        assertThat(parsed.missingFields()).isEmpty();
    }

    @Test
    void shouldParseNextMondayMorningSickLeave() {
        ParsedLeaveRequest parsed = parser.parse("我想请下周一上午病假");

        assertThat(parsed.leaveType()).isEqualTo(LeaveType.SICK);
        assertThat(parsed.startTime()).isEqualTo(LocalDateTime.of(2026, 6, 15, 9, 0));
        assertThat(parsed.endTime()).isEqualTo(LocalDateTime.of(2026, 6, 15, 12, 0));
    }

    @Test
    void shouldParseNextTuesdayFromSundayAsTuesdayInTheComingWeek() {
        ParsedLeaveRequest parsed = parser.parse("帮我请这下周二的年假");

        assertThat(parsed.leaveType()).isEqualTo(LeaveType.ANNUAL);
        assertThat(parsed.startTime()).isEqualTo(LocalDateTime.of(2026, 6, 16, 9, 0));
        assertThat(parsed.endTime()).isEqualTo(LocalDateTime.of(2026, 6, 16, 18, 0));
    }

    @Test
    void shouldParseIsoDateAnnualLeave() {
        ParsedLeaveRequest parsed = parser.parse("帮我请2026-06-23一天年假，原因是个人事务");

        assertThat(parsed.leaveType()).isEqualTo(LeaveType.ANNUAL);
        assertThat(parsed.startTime()).isEqualTo(LocalDateTime.of(2026, 6, 23, 9, 0));
        assertThat(parsed.endTime()).isEqualTo(LocalDateTime.of(2026, 6, 23, 18, 0));
        assertThat(parsed.missingFields()).isEmpty();
    }

    @Test
    void shouldParseChineseAbsoluteDateSickLeave() {
        ParsedLeaveRequest parsed = parser.parse("我想请2026年6月23日上午病假");

        assertThat(parsed.leaveType()).isEqualTo(LeaveType.SICK);
        assertThat(parsed.startTime()).isEqualTo(LocalDateTime.of(2026, 6, 23, 9, 0));
        assertThat(parsed.endTime()).isEqualTo(LocalDateTime.of(2026, 6, 23, 12, 0));
        assertThat(parsed.missingFields()).isEmpty();
    }

    @Test
    void shouldReportMissingLeaveTypeAndDate() {
        ParsedLeaveRequest parsed = parser.parse("我想请假");

        assertThat(parsed.missingFields()).containsExactly("请假类型", "请假日期");
    }
}
