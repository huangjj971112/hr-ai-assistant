package com.example.hrai.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.hrai.entity.Candidate;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CandidateRepository extends BaseMapper<Candidate> {
}
