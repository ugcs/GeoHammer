package com.ugcs.geohammer.service.magnetics;

import java.time.Instant;

public record RtpDirection(double inclination, double declination, Instant timestamp) {
}
