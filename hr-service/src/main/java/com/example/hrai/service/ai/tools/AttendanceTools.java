package com.example.hrai.service.ai.tools;

import com.example.hrai.dto.attendance.AttendanceRecordResponse;
import com.example.hrai.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AttendanceTools {

    private final AttendanceService attendanceService;

    public List<AttendanceRecordResponse> queryAttendanceRecords(
            String employeeName,
            LocalDate startDate,
            LocalDate endDate
    ) {
        return attendanceService.listRecords(employeeName, startDate, endDate);
    }
}
