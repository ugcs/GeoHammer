package com.ugcs.geohammer;
import java.util.concurrent.atomic.AtomicReference;

import com.ugcs.geohammer.util.Strings;
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
        if (Strings.isNullOrEmpty(buildVersion)) {
            return "Undefined";
        }
        String buildTimestamp = environment.getProperty("build.timestamp");
        if (!Strings.isNullOrEmpty(buildTimestamp)) {
            buildVersion = buildVersion.replace("SNAPSHOT", buildTimestamp);
        }
        return buildVersion;
    }
}