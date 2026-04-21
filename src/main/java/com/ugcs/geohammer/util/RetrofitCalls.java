package com.ugcs.geohammer.util;

import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.CompletableFuture;

public final class RetrofitCalls {

    private static final Logger log = LoggerFactory.getLogger(RetrofitCalls.class);

    private RetrofitCalls() {
    }

    public static <T> CompletableFuture<T> asCompletable(Call<T> call) {
        CompletableFuture<T> future = new CompletableFuture<>();
        call.enqueue(new Callback<>() {

            @Override
            public void onResponse(Call<T> call, Response<T> response) {
                if (response.isSuccessful() && response.body() != null) {
                    future.complete(response.body());
                } else {
                    Exception e = new RuntimeException("HTTP " + response.code());
                    future.completeExceptionally(e);
                }
            }

            @Override
            public void onFailure(Call<T> call, Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    public static <T> T call(Call<T> call) {
        Response<T> response;
        try {
            response = call.execute();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (response.isSuccessful()) {
            return response.body();
        }
        String errorMessage = "HTTP " + response.code() + " " + response.message();
        try (ResponseBody errorBody = response.errorBody()) {
            if (errorBody != null) {
                try {
                    String errorBodyString = errorBody.string();
                    if (!Strings.isNullOrBlank(errorBodyString)) {
                        errorMessage += ": " + errorBodyString;
                    }
                } catch (IOException e) {
                    log.warn("Cannot parse error body", e);
                }
            }
        }
        throw new RuntimeException(errorMessage);
    }
}
