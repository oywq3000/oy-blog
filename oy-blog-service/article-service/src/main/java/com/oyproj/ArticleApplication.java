package com.oyproj;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 文章管理模块
 */
@EnableFeignClients
@SpringBootApplication
public class ArticleApplication
{
    public static void main( String[] args )
    {
        SpringApplication.run(ArithmeticException.class,args);
    }
}
