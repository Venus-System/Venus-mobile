package com.venussystem.venusmobile.repository.api;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.venussystem.venusmobile.BuildConfig;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Cliente da Venus-AI-api. A URL vem do BuildConfig.VENUS_IA_URL: por padrao o
 * servidor da AWS, trocavel no local.properties ou no build (ver
 * app/build.gradle.kts). Se alguem a deixar vazia, o chat avisa que nao esta
 * conectado em vez de tentar uma URL falsa.
 */
public final class ClienteIa {

    // Conectar e rapido; o limite so pesa com o servidor fora do ar.
    private static final long LIMITE_CONEXAO = 30L;

    // A leitura e o tempo inteiro da resposta: a API so devolve quando a Venus
    // termina, e cada mensagem passa por varios modelos em sequencia (roteador,
    // especialista, juiz, memoria) - dezenas de segundos numa mensagem normal.
    // O valor sobrou do tempo em que a API acordava no Render (~100s a mais); na
    // AWS ela nao dorme, e a folga so cobre mensagens muito lentas.
    private static final long LIMITE_LEITURA = 240L;

    private static VenusIaApi instancia;

    private ClienteIa() {
    }

    public static boolean configurado() {
        return !BuildConfig.VENUS_IA_URL.trim().isEmpty();
    }

    /**
     * @return null quando nenhuma URL foi configurada.
     */
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

    /**
     * O Retrofit exige a URL base terminando em "/", e e facil esquecer isso
     * no local.properties.
     */
    @VisibleForTesting
    static String normalizar(String url) {
        String limpa = url.trim();
        return limpa.endsWith("/") ? limpa : limpa + "/";
    }

    private static VenusIaApi criar(String urlBase) {
        HttpLoggingInterceptor log = new HttpLoggingInterceptor();
        // BASIC nao registra corpo nem cabecalho, entao o token nao vai pro log.
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
