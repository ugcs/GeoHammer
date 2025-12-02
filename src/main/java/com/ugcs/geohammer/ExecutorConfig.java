package com.ugcs.geohammer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ExecutorConfig {

    @Primary
    @Bean(destroyMethod = "close")
    public ExecutorService executorService() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}