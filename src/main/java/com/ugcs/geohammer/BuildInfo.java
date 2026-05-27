package com.ugcs.geohammer;
import java.util.concurrent.atomic.AtomicReference;

import com.ugcs.geohammer.release.Version;
import com.ugcs.geohammer.util.Strings;
import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class BuildInfo {

    private final Environment environment;

    private final AtomicReference<Version> buildVersion = new AtomicReference<>();

    public BuildInfo(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    @Async
    public void init() {
        buildVersion.set(computeBuildVersion());
    }

    public Version getBuildVersion() {
        Version version = buildVersion.get();
        if (version == null) {
            version = computeBuildVersion();
            buildVersion.set(version);
        }
        return version;
    }

    private Version computeBuildVersion() {
        String versionString = environment.getProperty("build.version");
        if (Strings.isNullOrEmpty(versionString)) {
            return Version.UNDEFINED;
        }
        String buildTimestamp = environment.getProperty("build.timestamp");
        if (!Strings.isNullOrEmpty(buildTimestamp)) {
            versionString = versionString.replace("SNAPSHOT", buildTimestamp);
        }
        return Version.parse(versionString);
    }
}