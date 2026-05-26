package com.example.hrai.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.hrai.entity.UserAccount;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserAccountRepository extends BaseMapper<UserAccount> {
}
