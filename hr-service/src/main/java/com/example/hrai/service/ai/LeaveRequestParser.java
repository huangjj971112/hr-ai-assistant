package com.example.hrai.service.ai;

import com.example.hrai.entity.LeaveType;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LeaveRequestParser {

    private static final Pattern REASON_PATTERN = Pattern.compile("原因(?:是|：|:)?\\s*(.+)$");
    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("(?<!\\d)(\\d{4}-\\d{1,2}-\\d{1,2})(?!\\d)");
    private static final Pattern CHINESE_DATE_PATTERN = Pattern.compile("(\\d{4})年(\\d{1,2})月(\\d{1,2})日");
    private static final DateTimeFormatter ISO_DATE_FORMATTER = DateTimeFormatter.ofPattern("uuuu-M-d");

    private final Clock clock;

    public LeaveRequestParser(Clock clock) {
        this.clock = clock;
    }

    public ParsedLeaveRequest parse(String message) {
        LeaveType leaveType = extractLeaveType(message);
        LocalDate leaveDate = extractDate(message);
        List<String> missingFields = new ArrayList<>();
        if (leaveType == null) {
            missingFields.add("请假类型");
        }
        if (leaveDate == null) {
            missingFields.add("请假日期");
        }

        LocalDateTime startTime = null;
        LocalDateTime endTime = null;
        if (leaveDate != null) {
            if (message.contains("上午")) {
                startTime = leaveDate.atTime(9, 0);
                endTime = leaveDate.atTime(12, 0);
            } else if (message.contains("下午")) {
                startTime = leaveDate.atTime(14, 0);
                endTime = leaveDate.atTime(18, 0);
            } else {
                startTime = leaveDate.atTime(9, 0);
                endTime = leaveDate.atTime(18, 0);
            }
        }
        return new ParsedLeaveRequest(leaveType, startTime, endTime, extractReason(message), List.copyOf(missingFields));
    }

    private LeaveType extractLeaveType(String message) {
        if (message.contains("病假")) {
            return LeaveType.SICK;
        }
        if (message.contains("事假")) {
            return LeaveType.PERSONAL;
        }
        if (message.contains("年假")) {
            return LeaveType.ANNUAL;
        }
        return null;
    }

    private LocalDate extractDate(String message) {
        LocalDate absoluteDate = extractAbsoluteDate(message);
        if (absoluteDate != null) {
            return absoluteDate;
        }

        LocalDate today = LocalDate.now(clock);
        if (message.contains("下周")) {
            DayOfWeek dayOfWeek = extractDayOfWeek(message);
            if (dayOfWeek != null) {
                LocalDate nextWeekMonday = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
                return nextWeekMonday.plusDays(dayOfWeek.getValue() - DayOfWeek.MONDAY.getValue());
            }
        }
        if (message.contains("后天")) {
            return today.plusDays(2);
        }
        if (message.contains("明天")) {
            return today.plusDays(1);
        }
        if (message.contains("今天")) {
            return today;
        }
        return null;
    }

    private LocalDate extractAbsoluteDate(String message) {
        Matcher isoMatcher = ISO_DATE_PATTERN.matcher(message);
        if (isoMatcher.find()) {
            try {
                return LocalDate.parse(isoMatcher.group(1), ISO_DATE_FORMATTER);
            } catch (DateTimeException ignored) {
                return null;
            }
        }

        Matcher chineseMatcher = CHINESE_DATE_PATTERN.matcher(message);
        if (chineseMatcher.find()) {
            try {
                int year = Integer.parseInt(chineseMatcher.group(1));
                int month = Integer.parseInt(chineseMatcher.group(2));
                int day = Integer.parseInt(chineseMatcher.group(3));
                return LocalDate.of(year, month, day);
            } catch (DateTimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private DayOfWeek extractDayOfWeek(String message) {
        if (message.contains("周一")) {
            return DayOfWeek.MONDAY;
        }
        if (message.contains("周二")) {
            return DayOfWeek.TUESDAY;
        }
        if (message.contains("周三")) {
            return DayOfWeek.WEDNESDAY;
        }
        if (message.contains("周四")) {
            return DayOfWeek.THURSDAY;
        }
        if (message.contains("周五")) {
            return DayOfWeek.FRIDAY;
        }
        if (message.contains("周六")) {
            return DayOfWeek.SATURDAY;
        }
        if (message.contains("周日") || message.contains("周天")) {
            return DayOfWeek.SUNDAY;
        }
        return null;
    }

    private String extractReason(String message) {
        Matcher matcher = REASON_PATTERN.matcher(message);
        if (matcher.find() && !matcher.group(1).isBlank()) {
            return matcher.group(1).trim();
        }
        return "员工通过 AI 助手申请";
    }
}
