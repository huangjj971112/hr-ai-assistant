package com.example.hrai.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.hrai.entity.AttendanceRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AttendanceRecordRepository extends BaseMapper<AttendanceRecord> {
}
