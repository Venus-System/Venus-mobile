package com.venussystem.venusmobile.repository;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.venussystem.venusmobile.domain.match.CalculadoraMatch;
import com.venussystem.venusmobile.domain.match.EtiquetasDoPerfil;
import com.venussystem.venusmobile.domain.match.IngredienteAnalisado;
import com.venussystem.venusmobile.domain.match.ResultadoMatch;
import com.venussystem.venusmobile.repository.api.ClienteApi;
import com.venussystem.venusmobile.repository.api.MapeadorPerfilApi;
import com.venussystem.venusmobile.repository.api.VenusApi;
import com.venussystem.venusmobile.repository.api.dto.IngredientEffectResponse;
import com.venussystem.venusmobile.repository.api.dto.IngredientResponse;
import com.venussystem.venusmobile.repository.api.dto.PreferenceCatalogResponse;
import com.venussystem.venusmobile.repository.api.dto.ProductFullResponse;
import com.venussystem.venusmobile.repository.api.dto.ProductIngredientResponse;
import com.venussystem.venusmobile.repository.api.dto.ProductScoreResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import retrofit2.Response;

/**
 * Monta a aba "Match com voce": le a composicao do produto, os efeitos que o
 * catalogo registra para cada ingrediente e cruza com as etiquetas e alergias
 * do aparelho. Nao grava nada na API.
 */
public class MatchProdutoRepository {

    public interface AoCalcular {
        /** nota pode ser null: produto sem score no catalogo. */
        void aoConcluir(@NonNull ResultadoMatch resultado, @Nullable ProductScoreResponse nota,
                        @NonNull List<String> consideradas);

        void aoFalhar();
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final ExecutorService REDE = Executors.newFixedThreadPool(6);
    private static final Handler PRINCIPAL = new Handler(Looper.getMainLooper());

    private final VenusApi api;
    private final MapeadorPerfilApi.Respostas respostas;
    private final boolean questionarioRespondido;

    public MatchProdutoRepository(PerfilRepository perfil) {
        this(ClienteApi.get(), perfil, perfil.jaRespondeuQuestionario());
    }

    public MatchProdutoRepository(VenusApi api, MapeadorPerfilApi.Respostas respostas,
                                  boolean questionarioRespondido) {
        this.api = api;
        this.respostas = respostas;
        this.questionarioRespondido = questionarioRespondido;
    }

    public void calcular(long produtoId, @NonNull AoCalcular callback) {
        EXECUTOR.execute(() -> {
            try {
                Resultado resultado = calcularAgora(produtoId);
                PRINCIPAL.post(() -> callback.aoConcluir(
                        resultado.match, resultado.nota, resultado.consideradas));
            } catch (IOException | RuntimeException | InterruptedException
                     | ExecutionException e) {
                PRINCIPAL.post(callback::aoFalhar);
            }
        });
    }

    private Resultado calcularAgora(long produtoId)
            throws IOException, InterruptedException, ExecutionException {
        Response<ProductFullResponse> completo = api.buscarProdutoCompleto(produtoId).execute();
        if (!completo.isSuccessful() || completo.body() == null) {
            throw new IOException("produto " + completo.code());
        }
        ProductFullResponse produto = completo.body();
        ProductScoreResponse nota = produto.score;

        if (!questionarioRespondido) {
            return new Resultado(CalculadoraMatch.calcular(false, new HashMap<>(),
                    new HashSet<>(), new HashSet<>(), new ArrayList<>()), nota, new ArrayList<>());
        }

        Long versaoId = produto.currentVersion == null ? null : produto.currentVersion.id;
        if (versaoId == null) {
            Response<com.venussystem.venusmobile.repository.api.dto.ProductVersionResponse>
                    versao = api.versaoAtual(produtoId).execute();
            if (!versao.isSuccessful() || versao.body() == null || versao.body().id == null) {
                throw new IOException("versao " + versao.code());
            }
            versaoId = versao.body().id;
        }

        Set<String> slugsDoPerfil = EtiquetasDoPerfil.slugs(respostas);
        Map<Long, String> catalogo = catalogoDeEtiquetas();
        Set<String> alergias = EtiquetasDoPerfil.alergias(respostas);

        Response<List<ProductIngredientResponse>> composicao =
                api.listarIngredientesDaVersao(versaoId).execute();
        if (!composicao.isSuccessful() || composicao.body() == null) {
            throw new IOException("composicao " + composicao.code());
        }

        List<Future<IngredienteAnalisado>> pedidos = new ArrayList<>();
        for (ProductIngredientResponse item : composicao.body()) {
            if (item == null || item.ingredientId == null) continue;
            long ingredienteId = item.ingredientId;
            // Os efeitos sao buscados mesmo sem etiqueta do perfil: viram os destaques.
            pedidos.add(REDE.submit(() -> analisar(ingredienteId)));
        }

        List<IngredienteAnalisado> ingredientes = new ArrayList<>();
        int falhas = 0;
        for (Future<IngredienteAnalisado> pedido : pedidos) {
            IngredienteAnalisado ingrediente = pedido.get();
            if (ingrediente == null) {
                falhas++;
            } else {
                ingredientes.add(ingrediente);
            }
        }
        // Metade ou mais sem resposta: o resultado enganaria, melhor avisar o erro.
        if (!pedidos.isEmpty() && falhas * 2 >= pedidos.size()) {
            throw new IOException("ingredientes sem resposta");
        }

        // As alergias tem cartao proprio na tela; aqui so o que entrou no cruzamento.
        List<String> consideradas =
                EtiquetasDoPerfil.rotulos(slugsDoPerfil, Collections.emptySet());
        return new Resultado(
                CalculadoraMatch.calcular(true, catalogo, slugsDoPerfil, alergias, ingredientes),
                nota, consideradas);
    }

    /** id -> slug de todas as profile-tags: os destaques precisam do slug de cada efeito. */
    private Map<Long, String> catalogoDeEtiquetas() throws IOException {
        Response<List<PreferenceCatalogResponse>> catalogo = api.listarEtiquetas(500).execute();
        if (!catalogo.isSuccessful() || catalogo.body() == null) {
            throw new IOException("etiquetas " + catalogo.code());
        }
        Map<Long, String> slugs = new HashMap<>();
        for (PreferenceCatalogResponse etiqueta : catalogo.body()) {
            if (etiqueta != null && etiqueta.id != null && etiqueta.slug != null) {
                slugs.put(etiqueta.id, etiqueta.slug);
            }
        }
        return slugs;
    }

    /** null quando a API nao respondeu para este ingrediente. */
    @Nullable
    private IngredienteAnalisado analisar(long ingredienteId) {
        try {
            Response<IngredientResponse> dados = api.buscarIngrediente(ingredienteId).execute();
            if (!dados.isSuccessful() || dados.body() == null) return null;

            IngredientResponse ingrediente = dados.body();
            String nome = ingrediente.inciName != null && !ingrediente.inciName.trim().isEmpty()
                    ? ingrediente.inciName : ingrediente.commonName;

            Response<List<IngredientEffectResponse>> lista =
                    api.listarEfeitosDoIngrediente(ingredienteId).execute();
            if (!lista.isSuccessful() || lista.body() == null) return null;

            List<IngredienteAnalisado.Efeito> efeitos = new ArrayList<>();
            Map<Long, Boolean> vistos = new HashMap<>();
            for (IngredientEffectResponse e : lista.body()) {
                if (e == null || e.id == null || e.profileTagId == null
                        || vistos.put(e.id, true) != null) {
                    continue;
                }
                efeitos.add(new IngredienteAnalisado.Efeito(e.profileTagId,
                        e.effectCategory, e.effectName, e.effectDescription));
            }
            return new IngredienteAnalisado(nome, efeitos);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static final class Resultado {
        final ResultadoMatch match;
        @Nullable
        final ProductScoreResponse nota;
        final List<String> consideradas;

        Resultado(ResultadoMatch match, @Nullable ProductScoreResponse nota,
                  List<String> consideradas) {
            this.match = match;
            this.nota = nota;
            this.consideradas = consideradas;
        }
    }
}
