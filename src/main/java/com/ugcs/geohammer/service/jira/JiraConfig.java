package com.ugcs.geohammer.service.jira;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.converter.scalars.ScalarsConverterFactory;

@Configuration
public class JiraConfig {

    @Value("${jira.url}")
    private String jiraUrl;

    @Bean
    public JiraCollector jiraCollector() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(jiraUrl)
                .addConverterFactory(ScalarsConverterFactory.create())
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        return retrofit.create(JiraCollector.class);
    }
}
