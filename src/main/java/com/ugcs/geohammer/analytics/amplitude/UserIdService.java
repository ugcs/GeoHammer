package com.ugcs.geohammer.analytics.amplitude;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UserIdService {

    @Autowired
    private UserPropertiesService userPropertiesService;

    private static final String USER_ID_KEY = "amplitudeUserId";

    public String getOrCreateUserId() {
        String userId = userPropertiesService.get(USER_ID_KEY);
        if (userId == null) {
            userId = UUID.randomUUID().toString();
            userPropertiesService.put(USER_ID_KEY, userId);
            userPropertiesService.save();
        }
        return userId;
    }
}