package com.venussystem.venusmobile.repository;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.venussystem.venusmobile.repository.api.ClienteApi;
import com.venussystem.venusmobile.repository.api.MapeadorPerfilApi;
import com.venussystem.venusmobile.repository.api.VenusApi;
import com.venussystem.venusmobile.repository.api.dto.AllergyResponse;
import com.venussystem.venusmobile.repository.api.dto.FatiaResponse;
import com.venussystem.venusmobile.repository.api.dto.PreferenceCatalogResponse;
import com.venussystem.venusmobile.repository.api.dto.UserAllergyRequest;
import com.venussystem.venusmobile.repository.api.dto.UserAllergyResponse;
import com.venussystem.venusmobile.repository.api.dto.UserPreferenceRequest;
import com.venussystem.venusmobile.repository.api.dto.UserProfileRequest;
import com.venussystem.venusmobile.repository.api.dto.UserProfileTagRequest;
import com.venussystem.venusmobile.repository.api.dto.UserProfileTagResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Supplier;

import retrofit2.Call;
import retrofit2.Response;

/**
 * Leva para o Venus-CRUD o que o app guardou no aparelho: garante que a pessoa
 * tem cadastro na API e envia as respostas do questionario que ainda nao
 * foram enviadas - perfil, preferencias, alergias e etiquetas de preferencia.
 *
 * Roda quieto, sem avisar a tela: o app funciona igual com ou sem esse envio,
 * e se falhar (sem rede, API dormindo) as respostas continuam marcadas como
 * pendentes e vao na proxima abertura.
 */
public class SincronizacaoRepository {

    // Uma fila so: a tela principal e a de perfil podem pedir juntas, e dois
    // envios em paralelo do mesmo perfil so gastariam o cold start duas vezes.
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    // O app nao pergunta a gravidade da alergia, e a API exige uma. HIGH foi a
    // decisao junto com a API de classificacao; o site pergunta e manda a sua.
    private static final String GRAVIDADE_PADRAO = "HIGH";

    private static final int TAMANHO_PAGINA = 100;
    private static final int LIMITE_PAGINAS = 20;

    private static final int NAO_ENCONTRADO = 404;
    private static final int CONFLITO = 409;

    private final PerfilRepository perfil;
    private final UsuarioApiRepository usuarios;
    private final VenusApi api;

    public SincronizacaoRepository(Context context) {
        this(new PerfilRepository(context), new UsuarioApiRepository(context), ClienteApi.get());
    }

    @VisibleForTesting
    public SincronizacaoRepository(PerfilRepository perfil, UsuarioApiRepository usuarios,
                                   VenusApi api) {
        this.perfil = perfil;
        this.usuarios = usuarios;
        this.api = api;
    }

    public void sincronizarEmSegundoPlano() {
        EXECUTOR.execute(this::sincronizarAgora);
    }

    /**
     * @return true se enviou o perfil nesta chamada.
     */
    @WorkerThread
    @VisibleForTesting
    public boolean sincronizarAgora() {
        // O cadastro vem antes de olhar o perfil de proposito: mesmo sem nada
        // para enviar, ter o id guardado e o que deixa o chat personalizado.
        Long userId = usuarios.obterIdSincrono();
        if (userId == null || !perfil.temAlteracaoParaEnviar()) {
            return false;
        }

        // A versao e lida antes das respostas: uma edicao que chegue durante o
        // envio sobe a versao e continua pendente.
        long versao = perfil.versaoAtual();
        UserProfileRequest corpoPerfil = MapeadorPerfilApi.perfil(userId, perfil);
        if (corpoPerfil == null) {
            return false;
        }
        UserPreferenceRequest corpoPreferencias = MapeadorPerfilApi.preferencias(userId, perfil);

        try {
            // Alergias antes das etiquetas: se o envio parar no meio, que pare
            // depois do que protege a pessoa.
            boolean enviou = gravar(api.atualizarPerfil(userId, corpoPerfil),
                    () -> api.criarPerfil(corpoPerfil))
                    && gravar(api.atualizarPreferencias(userId, corpoPreferencias),
                    () -> api.criarPreferencias(corpoPreferencias))
                    && sincronizarAlergias(userId)
                    && sincronizarEtiquetas(userId);
            if (enviou) {
                perfil.marcarEnviada(versao);
            }
            return enviou;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * PUT primeiro, porque depois do primeiro envio e sempre atualizacao; o 404
     * dele quer dizer que o registro ainda nao existe, e ai vai o POST.
     */
    private boolean gravar(Call<Void> atualizar, Supplier<Call<Void>> criar) throws IOException {
        Response<Void> resposta = atualizar.execute();
        if (resposta.code() != NAO_ENCONTRADO) {
            return resposta.isSuccessful();
        }

        resposta = criar.get().execute();
        if (resposta.isSuccessful()) {
            return true;
        }
        // O PUT nao achou o registro e o POST tambem recusou: o jeito mais
        // provavel e o id guardado nao existir mais na API (banco recriado).
        // Esquecer o id faz a proxima tentativa buscar o cadastro de novo.
        int codigo = resposta.code();
        if (codigo == NAO_ENCONTRADO || codigo == CONFLITO || codigo == 422) {
            usuarios.esquecerId();
        }
        return false;
    }

    private boolean sincronizarAlergias(long userId) throws IOException {
        Response<List<AllergyResponse>> catalogo = api.listarAlergias().execute();
        if (!catalogo.isSuccessful() || catalogo.body() == null) {
            return false;
        }
        Set<Long> escolhidas = MapeadorPerfilApi.alergiasEscolhidas(catalogo.body(), perfil);

        List<UserAllergyResponse> salvas =
                lerTodasAsPaginas(pagina -> api.alergiasDoUsuario(userId, pagina, TAMANHO_PAGINA));
        if (salvas == null) {
            return false;
        }
        Set<Long> naApi = new HashSet<>();
        for (UserAllergyResponse salva : salvas) {
            if (salva.allergyId != null) {
                naApi.add(salva.allergyId);
            }
        }

        return sincronizarConjunto(PerfilRepository.ALERGIAS_NA_API, escolhidas, naApi,
                id -> {
                    UserAllergyRequest corpo = new UserAllergyRequest();
                    corpo.userId = userId;
                    corpo.allergyId = id;
                    corpo.severity = GRAVIDADE_PADRAO;
                    return api.adicionarAlergia(corpo);
                },
                id -> api.removerAlergia(userId, id));
    }

    private boolean sincronizarEtiquetas(long userId) throws IOException {
        Response<List<PreferenceCatalogResponse>> catalogo =
                api.listarEtiquetasDePreferencia().execute();
        if (!catalogo.isSuccessful() || catalogo.body() == null) {
            return false;
        }
        Set<Long> escolhidas = MapeadorPerfilApi.etiquetasEscolhidas(catalogo.body(), perfil);

        List<UserProfileTagResponse> salvas =
                lerTodasAsPaginas(pagina -> api.etiquetasDoUsuario(userId, pagina, TAMANHO_PAGINA));
        if (salvas == null) {
            return false;
        }
        Set<Long> naApi = new HashSet<>();
        for (UserProfileTagResponse salva : salvas) {
            if (salva.profileTagId != null) {
                naApi.add(salva.profileTagId);
            }
        }

        return sincronizarConjunto(PerfilRepository.ETIQUETAS_NA_API, escolhidas, naApi,
                id -> {
                    UserProfileTagRequest corpo = new UserProfileTagRequest();
                    corpo.userId = userId;
                    corpo.profileTagId = id;
                    return api.adicionarEtiqueta(corpo);
                },
                id -> api.removerEtiqueta(userId, id));
    }

    /**
     * Deixa na API o que a pessoa escolheu no app: grava o que falta e apaga o
     * que ela tirou.
     *
     * So apaga o que o proprio app gravou antes (guardado no PerfilRepository).
     * O app nao le de volta o que foi cadastrado pelo site, entao nao mostra
     * isso para a pessoa - e apagar uma alergia que ela nunca viu aqui seria o
     * pior erro possivel. O que veio do site fica como esta.
     */
    private boolean sincronizarConjunto(String chave, Set<Long> escolhidos, Set<Long> naApi,
                                        Function<Long, Call<Void>> adicionar,
                                        Function<Long, Call<Void>> remover) throws IOException {
        Set<Long> gravadosAntes = perfil.getIdsNaApi(chave);
        Set<Long> gravados = new HashSet<>(gravadosAntes);

        // O registro e salvo mesmo se parar no meio: o que ja foi gravado ou
        // apagado nesta vez precisa estar la na proxima tentativa.
        try {
            for (Long id : escolhidos) {
                if (!naApi.contains(id)) {
                    int codigo = adicionar.apply(id).execute().code();
                    // 409: outra tentativa (ou o site) gravou antes; ja esta la.
                    if (!sucesso(codigo) && codigo != CONFLITO) {
                        return false;
                    }
                }
                gravados.add(id);
            }

            for (Long id : gravadosAntes) {
                if (escolhidos.contains(id)) {
                    continue;
                }
                if (naApi.contains(id)) {
                    int codigo = remover.apply(id).execute().code();
                    if (!sucesso(codigo) && codigo != NAO_ENCONTRADO) {
                        return false;
                    }
                }
                gravados.remove(id);
            }
            return true;
        } finally {
            perfil.salvarIdsNaApi(chave, gravados);
        }
    }

    private interface Pagina<T> {
        Call<FatiaResponse<T>> buscar(int numero);
    }

    /**
     * As listas por usuario sao paginadas (Slice do Spring). Na pratica cabe
     * tudo na primeira pagina, mas ler so ela faria o app regravar o que
     * estivesse na segunda.
     *
     * @return null se a API respondeu erro.
     */
    @Nullable
    private static <T> List<T> lerTodasAsPaginas(Pagina<T> pagina) throws IOException {
        List<T> itens = new ArrayList<>();
        for (int numero = 0; numero < LIMITE_PAGINAS; numero++) {
            Response<FatiaResponse<T>> resposta = pagina.buscar(numero).execute();
            if (!resposta.isSuccessful()) {
                return null;
            }
            FatiaResponse<T> fatia = resposta.body();
            if (fatia == null || fatia.content == null) {
                return itens;
            }
            itens.addAll(fatia.content);

            boolean temMais = Boolean.FALSE.equals(fatia.last)
                    && fatia.content.size() == TAMANHO_PAGINA;
            if (!temMais) {
                return itens;
            }
        }
        return itens;
    }

    private static boolean sucesso(int codigo) {
        return codigo >= 200 && codigo < 300;
    }
}
