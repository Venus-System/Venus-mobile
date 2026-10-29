package com.venussystem.venusmobile.repository.api;

import androidx.annotation.Nullable;

import com.venussystem.venusmobile.BuildConfig;
import com.venussystem.venusmobile.repository.SessaoFirebase;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class ClienteClassificacao {

    private static final long LIMITE_CONEXAO = 30L;

    private static final long LIMITE_LEITURA = 120L;
    private static final long LIMITE_TOTAL = 150L;

    private static VenusClassificacaoApi instancia;

    private ClienteClassificacao() {
    }

    public static boolean configurado() {
        return !BuildConfig.VENUS_CLASSIFICACAO_URL.trim().isEmpty();
    }

    @Nullable
    public static synchronized VenusClassificacaoApi get() {
        if (!configurado()) {
            return null;
        }
        if (instancia == null) {
            instancia = criar(ClienteIa.normalizar(BuildConfig.VENUS_CLASSIFICACAO_URL));
        }
        return instancia;
    }

    private static VenusClassificacaoApi criar(String urlBase) {
        HttpLoggingInterceptor log = new HttpLoggingInterceptor();
        log.setLevel(BuildConfig.DEBUG
                ? HttpLoggingInterceptor.Level.BASIC
                : HttpLoggingInterceptor.Level.NONE);

        TokenFirebase token = new TokenFirebase(new SessaoFirebase());

        OkHttpClient http = new OkHttpClient.Builder()
                .connectTimeout(LIMITE_CONEXAO, TimeUnit.SECONDS)
                .readTimeout(LIMITE_LEITURA, TimeUnit.SECONDS)
                .callTimeout(LIMITE_TOTAL, TimeUnit.SECONDS)
                .addInterceptor(token)
                .authenticator(token)
                .addInterceptor(log)
                .build();

        return new Retrofit.Builder()
                .baseUrl(urlBase)
                .client(http)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(VenusClassificacaoApi.class);
    }
}
