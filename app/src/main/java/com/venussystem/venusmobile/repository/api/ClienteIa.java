package com.venussystem.venusmobile.repository.api;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.venussystem.venusmobile.BuildConfig;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class ClienteIa {
    private static final long LIMITE_CONEXAO = 30L;

    private static final long LIMITE_LEITURA = 240L;

    private static VenusIaApi instancia;

    private ClienteIa() {
    }

    public static boolean configurado() {
        return !BuildConfig.VENUS_IA_URL.trim().isEmpty();
    }

    @Nullable
    public static synchronized VenusIaApi get() {
        if (!configurado()) {
            return null;
        }
        if (instancia == null) {
            instancia = criar(normalizar(BuildConfig.VENUS_IA_URL));
        }
        return instancia;
    }

    @VisibleForTesting
    static String normalizar(String url) {
        String limpa = url.trim();
        return limpa.endsWith("/") ? limpa : limpa + "/";
    }

    private static VenusIaApi criar(String urlBase) {
        HttpLoggingInterceptor log = new HttpLoggingInterceptor();
        log.setLevel(BuildConfig.DEBUG
                ? HttpLoggingInterceptor.Level.BASIC
                : HttpLoggingInterceptor.Level.NONE);

        OkHttpClient http = new OkHttpClient.Builder()
                .connectTimeout(LIMITE_CONEXAO, TimeUnit.SECONDS)
                .readTimeout(LIMITE_LEITURA, TimeUnit.SECONDS)
                .addInterceptor(log)
                .build();

        return new Retrofit.Builder()
                .baseUrl(urlBase)
                .client(http)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(VenusIaApi.class);
    }
}
