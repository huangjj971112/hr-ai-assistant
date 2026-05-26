package com.example.hrai.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.example.hrai.repository")
public class MybatisPlusConfig {
}
