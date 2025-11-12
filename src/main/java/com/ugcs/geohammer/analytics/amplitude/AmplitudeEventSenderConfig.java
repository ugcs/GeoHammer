package com.ugcs.geohammer.analytics.amplitude;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource({"classpath:analytics.properties"})
public class AmplitudeEventSenderConfig {

    @Bean
    public AmplitudeEventSender amplitudeEventSender(
            @Value("${amplitude.api-key:}") String apiKey,
            @Value("${amplitude.enabled:}") Boolean enabled) {
        return new AmplitudeEventSender(apiKey, enabled);
    }
}
