package com.venussystem.venusmobile.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.venussystem.venusmobile.domain.match.QuandoClassificar;
import com.venussystem.venusmobile.repository.api.ClienteApi;
import com.venussystem.venusmobile.repository.api.ClienteClassificacao;
import com.venussystem.venusmobile.repository.api.VenusApi;
import com.venussystem.venusmobile.repository.api.VenusClassificacaoApi;
import com.venussystem.venusmobile.repository.api.dto.ClassificationRequest;
import com.venussystem.venusmobile.repository.api.dto.ClassificationResponse;
import com.venussystem.venusmobile.repository.api.dto.ProductFullResponse;
import com.venussystem.venusmobile.repository.api.dto.ProductVersionResponse;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.ResponseBody;
import retrofit2.Response;

public class ClassificacaoRepository {

    public interface AoClassificar {
        void aoConcluir(@NonNull ClassificationResponse classificacao);

        void aoFalhar(@Nullable String codigo);
    }

    @VisibleForTesting
    static final class Resultado {
        @Nullable
        final ClassificationResponse classificacao;
        @Nullable
        final String codigoDoErro;

        private Resultado(@Nullable ClassificationResponse classificacao,
                          @Nullable String codigoDoErro) {
            this.classificacao = classificacao;
            this.codigoDoErro = codigoDoErro;
        }

        static Resultado ok(ClassificationResponse classificacao) {
            return new Resultado(classificacao, null);
        }

        static Resultado falha(@Nullable String codigo) {
            return new Resultado(null, codigo);
        }
    }

    private static final String SEM_ANALISE = "ANALYSIS_NOT_FOUND";

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler PRINCIPAL = new Handler(Looper.getMainLooper());

    @Nullable
    private final VenusClassificacaoApi classificacao;
    private final VenusApi crud;
    private final UsuarioApiRepository usuarios;
    private final PerfilRepository perfil;
    private final AnaliseDoPostRepository doPost;

    public ClassificacaoRepository(Context context) {
        this(ClienteClassificacao.get(), ClienteApi.get(), new UsuarioApiRepository(context),
                new PerfilRepository(context), new AnaliseDoPostRepository(context));
    }

    public ClassificacaoRepository(@Nullable VenusClassificacaoApi classificacao, VenusApi crud,
                                   UsuarioApiRepository usuarios, PerfilRepository perfil,
                                   AnaliseDoPostRepository doPost) {
        this.classificacao = classificacao;
        this.crud = crud;
        this.usuarios = usuarios;
        this.perfil = perfil;
        this.doPost = doPost;
    }

    public void classificar(long produtoId, @NonNull AoClassificar callback) {
        EXECUTOR.execute(() -> {
            Resultado resultado;
            try {
                resultado = classificarAgora(produtoId);
            } catch (IOException | RuntimeException e) {
                resultado = Resultado.falha(null);
            }
            Resultado fim = resultado;
            PRINCIPAL.post(() -> {
                if (fim.classificacao != null && fim.classificacao.finalScore != null) {
                    callback.aoConcluir(fim.classificacao);
                } else {
                    callback.aoFalhar(fim.codigoDoErro);
                }
            });
        });
    }

    @VisibleForTesting
    @NonNull
    Resultado classificarAgora(long produtoId) throws IOException {
        if (classificacao == null || !perfil.jaRespondeuQuestionario()) return Resultado.falha(null);
        Long userId = usuarios.obterIdSincrono();
        if (userId == null) return Resultado.falha(null);

        Response<ClassificationResponse> salva =
                classificacao.ultimaClassificacao(userId, produtoId).execute();
        ClassificationResponse anterior = salva.isSuccessful() ? salva.body() : null;
        if (anterior != null) doPost.completar(anterior);

        if (anterior != null && !QuandoClassificar.precisaRecalcular(anterior.calculatedAt,
                perfil.alteradoEm(), perfil.temAlteracaoParaEnviar())) {
            return Resultado.ok(anterior);
        }
        if (anterior == null) {
            String codigo = codigoDoErro(salva.errorBody());
            if (!SEM_ANALISE.equals(codigo)) return Resultado.falha(codigo);
        }

        Long versao = versaoAtual(produtoId);
        if (versao == null) return anterior == null ? Resultado.falha(null) : Resultado.ok(anterior);

        Response<ClassificationResponse> nova =
                classificacao.classificar(new ClassificationRequest(userId, versao)).execute();
        if (nova.isSuccessful() && nova.body() != null) {
            doPost.guardar(nova.body());
            return Resultado.ok(nova.body());
        }
        return anterior == null
                ? Resultado.falha(codigoDoErro(nova.errorBody()))
                : Resultado.ok(anterior);
    }

    @Nullable
    private Long versaoAtual(long produtoId) throws IOException {
        ProductFullResponse completo = ProdutoCompletoCache.obter(crud, produtoId);
        if (completo != null && completo.currentVersion != null) {
            return completo.currentVersion.id;
        }
        Response<ProductVersionResponse> versao = crud.versaoAtual(produtoId).execute();
        return versao.isSuccessful() && versao.body() != null ? versao.body().id : null;
    }

    @Nullable
    private static String codigoDoErro(@Nullable ResponseBody corpo) {
        if (corpo == null) return null;
        try {
            return codigoDoErro(corpo.string());
        } catch (IOException e) {
            return null;
        }
    }

    @VisibleForTesting
    @Nullable
    static String codigoDoErro(@Nullable String json) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            JsonElement raiz = JsonParser.parseString(json);
            if (!raiz.isJsonObject()) return null;
            JsonObject objeto = raiz.getAsJsonObject();
            JsonElement code = objeto.get("code");
            return code == null || code.isJsonNull() ? null : code.getAsString();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
