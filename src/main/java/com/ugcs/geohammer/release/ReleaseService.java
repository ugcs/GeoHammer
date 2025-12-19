package com.ugcs.geohammer.release;

import com.ugcs.geohammer.BuildInfo;
import com.ugcs.geohammer.service.github.GitHubApi;
import com.ugcs.geohammer.service.github.Release;
import com.ugcs.geohammer.util.RetrofitCalls;
import org.springframework.stereotype.Service;
import retrofit2.Call;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ReleaseService {

    private static final String GITHUB_OWNER = "ugcs";

    private static final String GITHUB_REPOSITORY = "GeoHammer";

    private static final int DEFAULT_NUM_RELEASES = 20;

    private final BuildInfo buildInfo;

    private final GitHubApi gitHubApi;

    // cached releases response
    private final AtomicReference<CompletableFuture<List<Release>>> releases = new AtomicReference<>();

    public ReleaseService(BuildInfo buildInfo, GitHubApi gitHubApi) {
        this.buildInfo = buildInfo;
        this.gitHubApi = gitHubApi;
    }

    public CompletableFuture<List<Release>> getReleases() {
        return releases.updateAndGet(v -> {
            if (v == null || v.isCompletedExceptionally()) {
                Call<List<Release>> call = gitHubApi.getReleases(
                        GITHUB_OWNER,
                        GITHUB_REPOSITORY,
                        DEFAULT_NUM_RELEASES);
                v = RetrofitCalls.asCompletable(call);
            }
            return v;
        });
    }

    // whether release version matches current build version
    public boolean isCurrent(Release release) {
        return release != null && Objects.equals(release.getBuildVersion(), buildInfo.getBuildVersion());
    }
}
