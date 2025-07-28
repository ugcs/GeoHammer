package com.ugcs.gprvisualizer.app.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class PublicIpAddressProvider {
    private static final Logger log = LoggerFactory.getLogger(PublicIpAddressProvider.class);
    private static final String IP_API_URL = "https://api.ipify.org";

    private final WebClient webClient;

    public PublicIpAddressProvider() {
        this.webClient = WebClient.builder()
                .baseUrl(IP_API_URL)
                .build();
    }

    public String getPublicIpAddress() {
        try {
            return fetchPublicIpAddress().block();
        } catch (Exception e) {
            log.warn("Failed to get IP from  service", e);
            return "UNKNOWN";
        }
    }

    private  Mono<String> fetchPublicIpAddress() {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("format", "json")
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> {
                    String ip = "UNKNOWN";
                    try {
                        JSONObject json = new JSONObject(response);
                        ip = json.getString("ip");
                    } catch (Exception e) {
                        log.error("Failed to parse geolocation response: {}", response, e);
                    }
                    return ip;
                });
    }
}