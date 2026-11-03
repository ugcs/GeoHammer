package com.ugcs.geohammer.analytics.amplitude;

import com.google.gson.annotations.SerializedName;
import com.ugcs.geohammer.util.RetrofitCalls;
import okhttp3.OkHttpClient;
import org.springframework.stereotype.Service;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class PublicIpAddressProvider {
    private static final String BASE_URL = "https://api.ipify.org/";
    private static final Integer TIMEOUT_SECONDS = 30;

    interface IpApi {
        @GET("?format=json")
        Call<IpResponse> getIp();
    }

    static class IpResponse {
        @SerializedName("ip")
        String ip;
    }

    private final IpApi ipApi;

    public PublicIpAddressProvider() {
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        this.ipApi = retrofit.create(IpApi.class);
    }

    public CompletableFuture<String> getPublicIpAddress() {
        return RetrofitCalls.asCompletable(ipApi.getIp())
                .thenApply(response -> response.ip);
    }
}