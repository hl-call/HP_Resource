package com.hooya.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan(basePackages = "com.hooya.mapper.cxtrade", sqlSessionFactoryRef = "cxtradeSqlSessionFactory")
public class CxtradeDataSourceConfig {

}