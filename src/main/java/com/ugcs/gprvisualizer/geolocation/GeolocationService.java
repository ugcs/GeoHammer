package com.ugcs.gprvisualizer.geolocation;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class GeolocationService {
    private static final Logger log = LoggerFactory.getLogger(GeolocationService.class);
    private static final String IP_GEOLOCATION_URL = "https://api.ipify.org";

    private final WebClient webClient;

    public GeolocationService() {
        this.webClient = WebClient.builder()
                .baseUrl(IP_GEOLOCATION_URL)
                .build();
    }

    public Mono<String> getIp() {
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
