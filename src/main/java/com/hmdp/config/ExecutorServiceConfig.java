package com.hmdp.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
//配置线程池
@Configuration
public class ExecutorServiceConfig {
    //配置线程池
    @Bean
    public ExecutorService executorService(){
        return Executors.newFixedThreadPool(10);
    }
}
