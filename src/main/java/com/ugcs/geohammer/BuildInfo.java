package com.ugcs.geohammer;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class BuildInfo {

    private final Environment environment;

    private final AtomicReference<String> buildVersion = new AtomicReference<>();

    public BuildInfo(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    @Async
    public void init() {
        buildVersion.set(computeBuildVersion());
    }

    public String getBuildVersion() {
        String version = buildVersion.get();
        return version != null ? version : computeBuildVersion();
    }

    private String computeBuildVersion() {
        String buildVersion = environment.getProperty("build.version");
        if (buildVersion == null) {
            return "Undefined";
        }
        String snapshotSuffix = "-SNAPSHOT";
        if (buildVersion.endsWith(snapshotSuffix)) {
            buildVersion = buildVersion.substring(0, buildVersion.length() - snapshotSuffix.length());
            String buildTimestamp = environment.getProperty("build.timestamp");
            if (buildTimestamp != null) {
                buildVersion += "." + buildTimestamp;
            }
        }
        return buildVersion;
    }
}