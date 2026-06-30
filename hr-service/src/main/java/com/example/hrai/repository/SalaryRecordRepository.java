package com.example.hrai.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.hrai.entity.SalaryRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工资明细表访问入口。
 */
@Mapper
public interface SalaryRecordRepository extends BaseMapper<SalaryRecord> {
}
