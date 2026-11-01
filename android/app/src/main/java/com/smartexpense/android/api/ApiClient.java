package com.smartexpense.android.api;

import com.smartexpense.android.BuildConfig;
import com.smartexpense.android.util.TokenManager;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.util.concurrent.TimeUnit;

/**
 * Retrofit API client singleton with JWT authorization interceptor.
 */
public class ApiClient {

    private static Retrofit retrofit;

    public static Retrofit getClient() {
        if (retrofit == null) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(BuildConfig.DEBUG ?
                    HttpLoggingInterceptor.Level.BASIC :
                    HttpLoggingInterceptor.Level.NONE);

            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(new OfflineMockInterceptor())
                    .addInterceptor(authInterceptor())
                    .addInterceptor(logging)
                    .connectTimeout(3, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .writeTimeout(5, TimeUnit.SECONDS)
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(BuildConfig.API_BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }

    private static Interceptor authInterceptor() {
        return chain -> {
            String token = TokenManager.getToken();
            Request.Builder builder = chain.request().newBuilder();
            if (token != null && !token.isEmpty()) {
                builder.addHeader("Authorization", "Bearer " + token);
            }
            return chain.proceed(builder.build());
        };
    }

    public static <T> T createService(Class<T> serviceClass) {
        return getClient().create(serviceClass);
    }

    // Reset client (e.g. after token refresh)
    public static void reset() {
        retrofit = null;
    }
}
