package com.ugcs.geohammer.service.github;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

import java.util.List;

public interface GitHubApi {

    @GET("repos/{owner}/{repo}/releases")
    Call<List<Release>> getReleases(
            @Path("owner") String owner,
            @Path("repo") String repository,
            @Query("per_page") Integer limit);
}
