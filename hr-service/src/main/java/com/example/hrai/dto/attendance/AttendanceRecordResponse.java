package com.example.hrai.dto.attendance;

import java.time.LocalDate;
import java.time.LocalTime;

public record AttendanceRecordResponse(
        String employeeName,
        LocalDate attendanceDate,
        LocalTime checkInTime,
        LocalTime checkOutTime,
        String status,
        String remark
) {
}
