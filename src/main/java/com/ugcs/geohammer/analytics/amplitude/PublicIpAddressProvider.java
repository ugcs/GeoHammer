package com.ugcs.geohammer.analytics.amplitude;

import com.google.gson.annotations.SerializedName;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;

import java.util.concurrent.TimeUnit;

@Service
public class PublicIpAddressProvider {
    private static final Logger log = LoggerFactory.getLogger(PublicIpAddressProvider.class);
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

    public String getPublicIpAddress() {
        try {
            Call<IpResponse> call = ipApi.getIp();
            Response<IpResponse> response = call.execute();
            if (response.isSuccessful() && response.body() != null) {
                return response.body().ip;
            }
        } catch (Exception e) {
            log.warn("Failed to get IP from service", e);
        }
        return "UNKNOWN";
    }
}