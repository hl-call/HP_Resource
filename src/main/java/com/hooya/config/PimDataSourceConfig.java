package com.hooya.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan(basePackages = "com.hooya.mapper.pim", sqlSessionFactoryRef = "pimSqlSessionFactory")
public class PimDataSourceConfig {
}