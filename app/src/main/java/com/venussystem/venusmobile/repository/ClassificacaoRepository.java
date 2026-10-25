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

/**
 * Nota pessoal da aba "Match com voce", da Venus-Classificacao: le a ultima
 * analise salva e so pede uma nova (POST, que grava no banco) quando nao ha
 * nenhuma ou quando o perfil mudou depois dela. Qualquer falha devolve
 * aoFalhar, e a aba fica com o calculo feito no aparelho.
 */
public class ClassificacaoRepository {

    public interface AoClassificar {
        void aoConcluir(@NonNull ClassificationResponse classificacao);

        void aoFalhar();
    }

    private static final String PERFIL_FORA_DA_API = "PROFILE_NOT_FOUND";

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler PRINCIPAL = new Handler(Looper.getMainLooper());

    @Nullable
    private final VenusClassificacaoApi classificacao;
    private final VenusApi crud;
    private final UsuarioApiRepository usuarios;
    private final PerfilRepository perfil;

    public ClassificacaoRepository(Context context) {
        this(ClienteClassificacao.get(), ClienteApi.get(), new UsuarioApiRepository(context),
                new PerfilRepository(context));
    }

    public ClassificacaoRepository(@Nullable VenusClassificacaoApi classificacao, VenusApi crud,
                                   UsuarioApiRepository usuarios, PerfilRepository perfil) {
        this.classificacao = classificacao;
        this.crud = crud;
        this.usuarios = usuarios;
        this.perfil = perfil;
    }

    public void classificar(long produtoId, @NonNull AoClassificar callback) {
        EXECUTOR.execute(() -> {
            ClassificationResponse resultado;
            try {
                resultado = classificarAgora(produtoId);
            } catch (IOException | RuntimeException e) {
                resultado = null;
            }
            ClassificationResponse fim = resultado;
            PRINCIPAL.post(() -> {
                if (fim != null && fim.finalScore != null) {
                    callback.aoConcluir(fim);
                } else {
                    callback.aoFalhar();
                }
            });
        });
    }

    @VisibleForTesting
    @Nullable
    ClassificationResponse classificarAgora(long produtoId) throws IOException {
        if (classificacao == null || !perfil.jaRespondeuQuestionario()) return null;
        Long userId = usuarios.obterIdSincrono();
        if (userId == null) return null;

        Response<ClassificationResponse> salva =
                classificacao.ultimaClassificacao(userId, produtoId).execute();
        ClassificationResponse anterior = salva.isSuccessful() ? salva.body() : null;

        if (anterior != null && !QuandoClassificar.precisaRecalcular(anterior.calculatedAt,
                perfil.alteradoEm(), perfil.temAlteracaoParaEnviar())) {
            return anterior;
        }
        if (anterior == null) {
            // So o 404 de "sem analise" pede um calculo. Sem perfil na API o POST
            // daria o mesmo 404, e outro erro e problema do servidor.
            if (salva.code() != 404 || PERFIL_FORA_DA_API.equals(codigoDoErro(salva.errorBody()))) {
                return null;
            }
        }

        Long versao = versaoAtual(produtoId);
        if (versao == null) return anterior;

        Response<ClassificationResponse> nova =
                classificacao.classificar(new ClassificationRequest(userId, versao)).execute();
        if (nova.isSuccessful() && nova.body() != null) return nova.body();
        // Calculo novo recusado (versao em revisao, sem ingredientes...): a salva ainda serve.
        return anterior;
    }

    @Nullable
    private Long versaoAtual(long produtoId) throws IOException {
        Response<ProductFullResponse> completo = crud.buscarProdutoCompleto(produtoId).execute();
        if (completo.isSuccessful() && completo.body() != null
                && completo.body().currentVersion != null) {
            return completo.body().currentVersion.id;
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

    /** O campo "code" do ErrorResponse da Classificacao, ou null. */
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
