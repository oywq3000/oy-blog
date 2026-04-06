package com.oyproj;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Hello world!
 *
 */
@SpringBootApplication
@EnableTransactionManagement
@EnableFeignClients(basePackages = "com.oyproj.api")
public class UserApplication
{
    public static void main( String[] args )
    {
        SpringApplication.run(UserApplication.class,args);
    }
}
