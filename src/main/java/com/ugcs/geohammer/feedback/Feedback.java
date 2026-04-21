package com.ugcs.geohammer.feedback;

public record Feedback(
        String name,
        String email,
        String subject,
        String message
) {
}
