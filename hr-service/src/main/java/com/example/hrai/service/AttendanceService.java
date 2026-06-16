package com.example.hrai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.hrai.dto.attendance.AttendanceRecordResponse;
import com.example.hrai.entity.AttendanceRecord;
import com.example.hrai.exception.BusinessException;
import com.example.hrai.repository.AttendanceRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRecordRepository attendanceRecordRepository;

    public List<AttendanceRecordResponse> listRecords(String employeeName, LocalDate startDate, LocalDate endDate) {
        DateRange dateRange = resolveDateRange(startDate, endDate);
        LambdaQueryWrapper<AttendanceRecord> query = new LambdaQueryWrapper<AttendanceRecord>()
                .eq(AttendanceRecord::getEmployeeName, employeeName)
                .orderByDesc(AttendanceRecord::getAttendanceDate);
        if (dateRange.startDate() != null) {
            query.ge(AttendanceRecord::getAttendanceDate, dateRange.startDate());
            query.le(AttendanceRecord::getAttendanceDate, dateRange.endDate());
        }
        return attendanceRecordRepository.selectList(query)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private AttendanceRecordResponse toResponse(AttendanceRecord record) {
        return new AttendanceRecordResponse(
                record.getEmployeeName(),
                record.getAttendanceDate(),
                record.getCheckInTime(),
                record.getCheckOutTime(),
                record.getStatus().name(),
                record.getRemark()
        );
    }

    private DateRange resolveDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null && endDate == null) {
            return new DateRange(null, null);
        }
        LocalDate resolvedStartDate = startDate == null ? endDate : startDate;
        LocalDate resolvedEndDate = endDate == null ? startDate : endDate;
        if (resolvedStartDate.isAfter(resolvedEndDate)) {
            throw new BusinessException("INVALID_ATTENDANCE_DATE_RANGE", "考勤查询开始日期不能晚于结束日期");
        }
        return new DateRange(resolvedStartDate, resolvedEndDate);
    }

    private record DateRange(LocalDate startDate, LocalDate endDate) {
    }
}
