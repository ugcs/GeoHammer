package com.ugcs.geohammer.service.jira;

import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface JiraCollector {

    @POST("rest/collectors/1.0/tempattachment/{collectorId}")
    Call<JiraTempFile> uploadTempFile(
            @Path("collectorId") String collectorId,
            @Query("filename") String fileName,
            @Query("size") long fileSize,
            @Body RequestBody file
    );

    @FormUrlEncoded
    @POST("rest/collectors/1.0/template/custom/{collectorId}")
    Call<String> submitFeedback(
            @Path("collectorId") String collectorId,
            @Field("customfield_12517") String product,
            @Field("summary") String summary,
            @Field("description") String description,
            @Field("fullname") String name,
            @Field("email") String email,
            @Field("filetoconvert") String tempFileId
    );
}

