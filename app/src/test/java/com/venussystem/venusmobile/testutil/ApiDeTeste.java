package com.venussystem.venusmobile.testutil;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockWebServer;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Retrofit de verdade apontando para o MockWebServer, com limites curtos para
 * um teste que trava falhar rapido em vez de esperar os minutos do app.
 */
public final class ApiDeTeste {

    public static final long LIMITE_SEGUNDOS = 2L;

    private ApiDeTeste() {
    }

    public static <T> T criar(MockWebServer server, Class<T> tipo) {
        return criar(server, tipo, LIMITE_SEGUNDOS);
    }

    public static <T> T criar(MockWebServer server, Class<T> tipo, long limiteLeituraSegundos) {
        OkHttpClient http = new OkHttpClient.Builder()
                .connectTimeout(LIMITE_SEGUNDOS, TimeUnit.SECONDS)
                .readTimeout(limiteLeituraSegundos, TimeUnit.SECONDS)
                .build();
        return new Retrofit.Builder()
                .baseUrl(server.url("/"))
                .client(http)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(tipo);
    }
}
