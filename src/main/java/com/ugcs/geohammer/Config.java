package com.ugcs.geohammer;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

import com.ugcs.geohammer.model.template.FileTemplates;

@Configuration
@EnableAsync
@PropertySource({"classpath:buildnumber.properties", "classpath:application.properties"})
public class Config implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor(); // FixedThreadPool(5);
    }

    @Bean
    InitializingBean initializingBean(FileTemplates templates) {
        return templates::watchTemplates;
    }
    
}