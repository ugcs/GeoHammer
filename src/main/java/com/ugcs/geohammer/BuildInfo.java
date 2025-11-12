package com.ugcs.geohammer;
import java.util.Optional;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class BuildInfo {

    private final Environment environment;

    public BuildInfo(Environment environment) {
        this.environment = environment;
    }

    public String getBuildVersion() {
        return Optional.ofNullable(environment.getProperty("build.version"))
            .orElse("Undefined")
            .replace("SNAPSHOT", getBuildTimestamp());
    }

    private String getBuildTimestamp() {
        return environment.getProperty("build.timestamp");
    }
}