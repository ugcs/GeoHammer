package com.ugcs.geohammer.util;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.util.concurrent.CompletableFuture;

public final class RetrofitCalls {

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
}
