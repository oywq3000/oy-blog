package com.oyproj;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
/**
 * 文章管理模块
 */
@EnableFeignClients(basePackages = "com.oyproj.api")
@SpringBootApplication
public class ArticleApplication
{
    public static void main( String[] args )
    {
        SpringApplication.run(ArticleApplication.class,args);
    }
}
