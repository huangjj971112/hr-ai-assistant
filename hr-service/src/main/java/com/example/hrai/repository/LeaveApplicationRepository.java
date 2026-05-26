package com.example.hrai.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.hrai.entity.LeaveApplication;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LeaveApplicationRepository extends BaseMapper<LeaveApplication> {
}
