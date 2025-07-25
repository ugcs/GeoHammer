package com.ugcs.gprvisualizer.geolocation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class IpProvider {
    private static final Logger log = LoggerFactory.getLogger(IpProvider.class);

    private final GeolocationService geolocationService;

    public IpProvider(GeolocationService geolocationService) {
        this.geolocationService = geolocationService;
    }

    public String getIpAddress() {
        if (geolocationService != null) {
            try {
                return geolocationService.getIp().block();
            } catch (Exception e) {
                log.warn("Failed to get IP from geolocation service", e);
            }
        }
        return "UNKNOWN";
    }
}
