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
 * aoFalhar com o code do erro, e a aba fica com o calculo feito no aparelho.
 */
public class ClassificacaoRepository {

    public interface AoClassificar {
        void aoConcluir(@NonNull ClassificationResponse classificacao);

        /** @param codigo o "code" do erro da Classificacao; null em falha de rede ou sem perfil. */
        void aoFalhar(@Nullable String codigo);
    }

    /** A analise, ou o code do erro quando ela nao veio. */
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

    // O 404 do GET que so diz que a pessoa nunca analisou o produto.
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
        // O GET nao traz a alergia nem os pontos; volta o que o POST dessa analise trouxe.
        if (anterior != null) doPost.completar(anterior);

        if (anterior != null && !QuandoClassificar.precisaRecalcular(anterior.calculatedAt,
                perfil.alteradoEm(), perfil.temAlteracaoParaEnviar())) {
            return Resultado.ok(anterior);
        }
        if (anterior == null) {
            // So "sem analise" pede um calculo. Produto, versao ou modelo que
            // faltam o POST repetiria, e outro erro e problema do servidor.
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
        // Calculo novo recusado (versao em revisao, sem ingredientes...): a salva ainda serve.
        return anterior == null
                ? Resultado.falha(codigoDoErro(nova.errorBody()))
                : Resultado.ok(anterior);
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
