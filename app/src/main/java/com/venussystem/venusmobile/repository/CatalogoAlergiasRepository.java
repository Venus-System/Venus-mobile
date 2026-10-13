package com.venussystem.venusmobile.repository;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.core.content.ContextCompat;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.repository.api.ClienteApi;
import com.venussystem.venusmobile.repository.api.VenusApi;
import com.venussystem.venusmobile.repository.api.dto.AllergyResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Response;

/**
 * As opcoes da pergunta de alergias, vindas do catalogo GET /api/allergies.
 *
 * O catalogo e o mesmo que o site mostra e que a analise sabe checar, entao
 * as duas telas oferecem sempre as mesmas alergias. A ultima resposta fica
 * guardada no aparelho: com o Render dormindo a API leva ~100s, e a tela nao
 * pode esperar isso para ter opcoes. Antes da primeira resposta vale a lista
 * reserva do arrays.xml.
 *
 * Nao e separado por conta (ver DadosDaConta): o catalogo e igual para todos.
 */
public class CatalogoAlergiasRepository {

    public interface AoAtualizar {
        void aoAtualizar(List<String> nomes);
    }

    private static final String ARQUIVO = "venus_catalogo_alergias";
    private static final String CHAVE_NOMES = "nomes";

    // O mesmo separador do PerfilRepository: nao aparece em nome de alergia.
    private static final String SEPARADOR = String.valueOf((char) 31);

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private final Context context;
    private final SharedPreferences prefs;
    private final VenusApi api;
    private final Executor principal;

    public CatalogoAlergiasRepository(Context context) {
        this(context, ClienteApi.get(), ContextCompat.getMainExecutor(context));
    }

    @VisibleForTesting
    public CatalogoAlergiasRepository(Context context, VenusApi api, Executor principal) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE);
        this.api = api;
        this.principal = principal;
    }

    /** O que mostrar agora: o ultimo catalogo recebido, ou a lista reserva. */
    public List<String> nomes() {
        String salvo = prefs.getString(CHAVE_NOMES, "");
        if (salvo != null && !salvo.isEmpty()) {
            return new ArrayList<>(Arrays.asList(salvo.split(SEPARADOR)));
        }
        return new ArrayList<>(Arrays.asList(
                context.getResources().getStringArray(R.array.alergias)));
    }

    /**
     * Busca o catalogo sem travar a tela e avisa na thread principal. Se a API
     * nao responder, nao avisa: a tela continua com o que ja mostrava.
     */
    public void atualizarEmSegundoPlano(AoAtualizar aoAtualizar) {
        EXECUTOR.execute(() -> {
            List<String> nomes = buscarAgora();
            if (nomes != null) {
                principal.execute(() -> aoAtualizar.aoAtualizar(nomes));
            }
        });
    }

    /**
     * @return os nomes do catalogo, ou null se a API nao respondeu ou mandou
     * uma lista vazia - sem nenhuma opcao a pergunta ficaria inutil, entao
     * vale mais continuar com a anterior.
     */
    @WorkerThread
    @VisibleForTesting
    @Nullable
    public List<String> buscarAgora() {
        Response<List<AllergyResponse>> resposta;
        try {
            resposta = api.listarAlergias().execute();
        } catch (IOException e) {
            return null;
        }
        if (!resposta.isSuccessful() || resposta.body() == null) {
            return null;
        }

        List<String> nomes = new ArrayList<>();
        for (AllergyResponse alergia : resposta.body()) {
            String nome = alergia.allergyName == null ? "" : alergia.allergyName.trim();
            if (!nome.isEmpty() && !nomes.contains(nome)) {
                nomes.add(nome);
            }
        }
        if (nomes.isEmpty()) {
            return null;
        }

        prefs.edit().putString(CHAVE_NOMES, String.join(SEPARADOR, nomes)).apply();
        return nomes;
    }
}
