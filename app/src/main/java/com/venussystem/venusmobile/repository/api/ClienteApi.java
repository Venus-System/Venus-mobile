package com.venussystem.venusmobile.repository.api;

import com.venussystem.venusmobile.BuildConfig;
import com.venussystem.venusmobile.repository.SessaoFirebase;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class ClienteApi {

    public static final String URL_BASE = "https://34-192-194-32.sslip.io/";

    private static final long LIMITE_CONEXAO = 30L;
    private static final long LIMITE_LEITURA = 180L;

    private static VenusApi instancia;
    private static ScanSubmissionApi scanApi;
    private static ScanIngredientApi ingredientApi;

    public static synchronized ScanIngredientApi ingredients() {
        if (ingredientApi == null) {
            OkHttpClient http = new OkHttpClient.Builder()
                    .connectTimeout(LIMITE_CONEXAO, TimeUnit.SECONDS)
                    .readTimeout(LIMITE_LEITURA, TimeUnit.SECONDS)
                    .callTimeout(190, TimeUnit.SECONDS)
                    .followRedirects(false).followSslRedirects(false).build();
            ingredientApi = new Retrofit.Builder().baseUrl(URL_BASE).client(http)
                    .addConverterFactory(GsonConverterFactory.create()).build().create(ScanIngredientApi.class);
        }
        return ingredientApi;
    }

    public static synchronized ScanSubmissionApi scans() {
        if (scanApi == null) {
            OkHttpClient http = new OkHttpClient.Builder()
                    .connectTimeout(LIMITE_CONEXAO, TimeUnit.SECONDS)
                    .readTimeout(LIMITE_LEITURA, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .followRedirects(false).followSslRedirects(false).build();
            scanApi = new Retrofit.Builder().baseUrl(URL_BASE).client(http)
                    .addConverterFactory(GsonConverterFactory.create()).build()
                    .create(ScanSubmissionApi.class);
        }
        return scanApi;
    }

    private ClienteApi() {
    }

    public static synchronized VenusApi get() {
        if (instancia == null) {
            instancia = criar();
        }
        return instancia;
    }

    private static VenusApi criar() {
        HttpLoggingInterceptor log = new HttpLoggingInterceptor();
        log.setLevel(BuildConfig.DEBUG
                ? HttpLoggingInterceptor.Level.BASIC
                : HttpLoggingInterceptor.Level.NONE);

        TokenFirebase token = new TokenFirebase(new SessaoFirebase());

        OkHttpClient http = new OkHttpClient.Builder()
                .connectTimeout(LIMITE_CONEXAO, TimeUnit.SECONDS)
                .readTimeout(LIMITE_LEITURA, TimeUnit.SECONDS)
                .addInterceptor(token)
                .authenticator(token)
                .addInterceptor(log)
                .build();

        return new Retrofit.Builder()
                .baseUrl(URL_BASE)
                .client(http)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(VenusApi.class);
    }
}
