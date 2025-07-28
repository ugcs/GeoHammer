package com.ugcs.gprvisualizer.app.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public class PublicIpAddressProvider {
    private static final Logger log = LoggerFactory.getLogger(PublicIpAddressProvider.class);
    private static final String IP_API_URL = "https://api.ipify.org?format=json";
    private static final Integer TIMEOUT_SECONDS = 30;

    private final HttpClient httpClient;

    public PublicIpAddressProvider() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .build();
    }

    public String getPublicIpAddress() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(IP_API_URL))
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JSONObject json = new JSONObject(response.body());
            return json.getString("ip");
        } catch (Exception e) {
            log.warn("Failed to get IP from service", e);
            return "UNKNOWN";
        }
    }
}