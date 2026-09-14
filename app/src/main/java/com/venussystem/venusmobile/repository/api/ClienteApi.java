package com.venussystem.venusmobile.repository.api;

import com.venussystem.venusmobile.BuildConfig;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class ClienteApi {

    public static final String URL_BASE = "https://venus-crud.onrender.com/";

    // A API esta hospedada no plano gratuito do Render, que derruba a instancia
    // quando ninguem usa. Acordar o servidor foi medido em ~100s, entao o limite
    // de leitura tem que ficar bem acima disso: com 90s o app desistia ANTES da
    // API responder e mostrava erro de conexao numa chamada que ia dar certo.
    //
    // Conectar e outra historia - o TCP responde em menos de 100ms mesmo com o
    // servidor dormindo, porque quem atende e a borda do Render. Se a conexao
    // demorar, o problema e a rede do aparelho, e ai nao vale esperar muito.
    private static final long LIMITE_CONEXAO = 30L;
    private static final long LIMITE_LEITURA = 180L;

    private static VenusApi instancia;

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

        OkHttpClient http = new OkHttpClient.Builder()
                .connectTimeout(LIMITE_CONEXAO, TimeUnit.SECONDS)
                .readTimeout(LIMITE_LEITURA, TimeUnit.SECONDS)
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
