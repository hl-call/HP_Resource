package com.hooya.config;

import com.alibaba.druid.spring.boot.autoconfigure.DruidDataSourceBuilder;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;

@Configuration
public class MybatisPlusConfig {
    @Primary
    @Bean(name = "pimDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.pim")
    public DataSource pimDataSource() {
        return DruidDataSourceBuilder.create().build();
    }

    @Primary
    @Bean(name = "pimSqlSessionFactory")
    public SqlSessionFactory pimSqlSessionFactory(@Qualifier("pimDataSource") DataSource dataSource) throws Exception {
        return createSqlSessionFactory(dataSource, "classpath:mapper/pim/*.xml", "com.hooya.domain");
    }

    @Bean(name = "cxtradeDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.cxtrade")
    public DataSource cxtraceDataSource() {
        return DruidDataSourceBuilder.create().build();
    }

    @Bean(name = "cxtradeSqlSessionFactory")
    public SqlSessionFactory cxtradeSqlSessionFactory(@Qualifier("cxtradeDataSource") DataSource dataSource) throws Exception {
        return createSqlSessionFactory(dataSource, "classpath:mapper/cxtrade/*.xml", "com.hooya.domain");
    }




    private SqlSessionFactory createSqlSessionFactory(DataSource dataSource) throws Exception {
        MybatisSqlSessionFactoryBean sessionFactory = new MybatisSqlSessionFactoryBean();
        sessionFactory.setDataSource(dataSource);

        // 其他MyBatis-Plus配置...
        MybatisConfiguration mybatisConfiguration = new MybatisConfiguration();

        mybatisConfiguration.setLogImpl(org.apache.ibatis.logging.log4j2.Log4j2Impl.class);

        mybatisConfiguration.setMapUnderscoreToCamelCase(true);
        mybatisConfiguration.setDefaultStatementTimeout(60);
        sessionFactory.setConfiguration(mybatisConfiguration);
        sessionFactory.setPlugins(mybatisPlusInterceptor());

        return sessionFactory.getObject();
    }

    private SqlSessionFactory createSqlSessionFactory(
            DataSource dataSource, String mapperLocations, String typeAliasesPackage) throws Exception {
        MybatisSqlSessionFactoryBean sessionFactory = new MybatisSqlSessionFactoryBean();
        sessionFactory.setDataSource(dataSource);
        sessionFactory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources(mapperLocations));
        sessionFactory.setTypeAliasesPackage(typeAliasesPackage);

        // 其他MyBatis-Plus配置...
        MybatisConfiguration mybatisConfiguration = new MybatisConfiguration();

        //TODO 关闭sql打印
        mybatisConfiguration.setLogImpl(org.apache.ibatis.logging.stdout.StdOutImpl.class);

        mybatisConfiguration.setMapUnderscoreToCamelCase(true);
        mybatisConfiguration.setDefaultStatementTimeout(300);
        sessionFactory.setConfiguration(mybatisConfiguration);
        sessionFactory.setPlugins(mybatisPlusInterceptor());

        return sessionFactory.getObject();
    }

    //分页
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor paginationInnerInterceptor = new PaginationInnerInterceptor();
        paginationInnerInterceptor.setOptimizeJoin(true);
        paginationInnerInterceptor.setDbType(DbType.MYSQL);
//        paginationInnerInterceptor.setDbType(DbType.SQL_SERVER);
        paginationInnerInterceptor.setOverflow(true);
        interceptor.addInnerInterceptor(paginationInnerInterceptor);
        OptimisticLockerInnerInterceptor optimisticLockerInnerInterceptor = new OptimisticLockerInnerInterceptor();
        interceptor.addInnerInterceptor(optimisticLockerInnerInterceptor);
        return interceptor;
    }

}