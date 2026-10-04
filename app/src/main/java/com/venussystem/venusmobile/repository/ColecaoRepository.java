package com.venussystem.venusmobile.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.Colecao;

import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Listas do proprio usuario, guardadas no aparelho, separadas por conta (ver
 * DadosDaConta): quem entra com outro e-mail no mesmo celular nao ve as listas
 * da conta anterior.
 *
 * O aparelho continua sendo a fonte do que a tela mostra. A copia em
 * /api/user-lists e mantida pela SincronizacaoListas, que usa o id da API e o
 * ultimo nome, descricao e capa padrao enviados, guardados aqui junto de cada
 * lista. A foto de capa escolhida pela pessoa ainda fica so no aparelho.
 */
public class ColecaoRepository {

    static final String ARQUIVO = "venus_listas";
    private static final String CHAVE_LISTAS = "minhas_listas";
    private static final String CHAVE_PROXIMO_ID = "proximo_id";
    private static final String CHAVE_APAGAR_NA_API = "apagar_na_api";

    // Cada conta comeca com estas 3, como ponto de partida. Depois disso quem
    // manda e o que estiver salvo (criar, editar capa, renomear, excluir).
    private static final List<ColecaoSalva> EXEMPLO = Arrays.asList(
            new ColecaoSalva(1L, "Produtos favoritados", null, "favoritos", null),
            new ColecaoSalva(2L, "Produtos escaneados", null, "escaneados", null),
            new ColecaoSalva(3L, "Rotina de skincare", null, "skincare", null)
    );

    private static final Type TIPO_LISTA_SALVA = new TypeToken<List<ColecaoSalva>>() {
    }.getType();

    // A tela e a sincronizacao (em outra thread) leem e regravam a mesma lista
    // de listas. Sem a trava, uma gravacao de uma apagava a da outra.
    private static final Object TRAVA = new Object();

    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    public ColecaoRepository(Context context) {
        this(context, DadosDaConta.uidAtual(context));
    }

    @VisibleForTesting
    public ColecaoRepository(Context context, @Nullable String uid) {
        this.prefs = DadosDaConta.prefs(context, ARQUIVO, uid);
    }

    /**
     * Apaga as listas de antes da separacao por conta, junto com as capas
     * delas: nao da para saber de qual conta eram. Ver DadosDaConta.
     */
    static void descartarListasSemDono(Context app) {
        SharedPreferences antigas = app.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE);
        String salvo = antigas.getString(CHAVE_LISTAS, null);
        if (salvo != null) {
            try {
                List<ColecaoSalva> listas = new Gson().fromJson(salvo, TIPO_LISTA_SALVA);
                if (listas != null) {
                    for (ColecaoSalva lista : listas) {
                        excluirArquivoDaCapa(lista.caminhoImagem);
                    }
                }
            } catch (JsonParseException ignorado) {
                // Sem conseguir ler, as capas ficam; as listas vao embora igual.
            }
        }
        app.deleteSharedPreferences(ARQUIVO);
    }

    public LiveData<List<Colecao>> minhasListas() {
        synchronized (TRAVA) {
            return new MutableLiveData<>(paraColecoes(carregar()));
        }
    }

    public Colecao criar(String nome, String descricao, @Nullable String caminhoImagem) {
        synchronized (TRAVA) {
            List<ColecaoSalva> atuais = carregar();
            long id = proximoId(atuais);
            ColecaoSalva nova = new ColecaoSalva(id, nome, descricao, null, caminhoImagem);

            List<ColecaoSalva> atualizadas = new ArrayList<>();
            atualizadas.add(nova);
            atualizadas.addAll(atuais);
            prefs.edit()
                    .putString(CHAVE_LISTAS, gson.toJson(atualizadas))
                    .putLong(CHAVE_PROXIMO_ID, id + 1)
                    .apply();

            return nova.paraColecao();
        }
    }

    /**
     * A API nao aceita duas listas com o mesmo nome para a mesma pessoa. A
     * tela checa antes de criar ou renomear, ignorando maiusculas e espacos,
     * para "Rotina" e "rotina " nao virarem duas listas que parecem iguais.
     *
     * @param ignorarId a propria lista, quando e uma renomeacao (-1 ao criar).
     */
    public boolean nomeEmUso(String nome, long ignorarId) {
        String procurado = comparavel(nome);
        synchronized (TRAVA) {
            for (ColecaoSalva c : carregar()) {
                if (c.id != ignorarId && comparavel(c.nome).equals(procurado)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Troca a capa de uma lista ja existente (a de exemplo ou uma criada).
     * Silenciosamente nao faz nada se o id nao existir mais - a tela que
     * chamou ja teria fechado nesse caso.
     */
    public void atualizarImagem(long id, String caminhoImagem) {
        synchronized (TRAVA) {
            List<ColecaoSalva> atualizadas = new ArrayList<>();
            for (ColecaoSalva c : carregar()) {
                atualizadas.add(c.id == id ? c.comImagem(caminhoImagem) : c);
            }
            salvar(atualizadas);
        }
    }

    public void renomear(long id, String novoNome) {
        synchronized (TRAVA) {
            List<ColecaoSalva> atualizadas = new ArrayList<>();
            for (ColecaoSalva c : carregar()) {
                atualizadas.add(c.id == id ? c.comNome(novoNome) : c);
            }
            salvar(atualizadas);
        }
    }

    public void atualizarDescricao(long id, String novaDescricao) {
        synchronized (TRAVA) {
            List<ColecaoSalva> atualizadas = new ArrayList<>();
            for (ColecaoSalva c : carregar()) {
                atualizadas.add(c.id == id ? c.comDescricao(novaDescricao) : c);
            }
            salvar(atualizadas);
        }
    }

    /**
     * Se a lista ja estava na API, o id dela entra numa fila para a
     * sincronizacao apagar la tambem.
     */
    public void excluir(long id) {
        synchronized (TRAVA) {
            List<ColecaoSalva> atualizadas = new ArrayList<>();
            for (ColecaoSalva c : carregar()) {
                if (c.id == id) {
                    excluirArquivoDaCapa(c.caminhoImagem);
                    if (c.idApi != null) {
                        adicionarParaApagar(c.idApi);
                    }
                } else {
                    atualizadas.add(c);
                }
            }
            salvar(atualizadas);
        }
    }

    // ---- O que a SincronizacaoListas usa ----

    /** Como cada lista esta agora, para decidir o que mandar para a API. */
    List<ListaLocal> paraSincronizar() {
        synchronized (TRAVA) {
            List<ListaLocal> listas = new ArrayList<>();
            for (ColecaoSalva c : carregar()) {
                listas.add(new ListaLocal(c.id, c.nome, semTextoVazio(c.descricao), c.chaveImagem,
                        c.idApi, c.nomeNaApi, c.descricaoNaApi, c.capaNaApi));
            }
            return listas;
        }
    }

    /**
     * Guarda o id que a API deu para a lista e o que foi mandado na criacao.
     *
     * @param enviada a lista como estava quando foi mandada. Se ela mudou no
     *                meio do envio, a mudanca continua pendente.
     * @return false se a lista foi excluida no aparelho enquanto era criada na
     * API: ai o id vai direto para a fila de apagar, senao ficaria orfa la.
     */
    boolean marcarCriadaNaApi(long idApi, ListaLocal enviada) {
        synchronized (TRAVA) {
            boolean achou = false;
            List<ColecaoSalva> atualizadas = new ArrayList<>();
            for (ColecaoSalva c : carregar()) {
                if (c.id == enviada.id) {
                    achou = true;
                    atualizadas.add(c.naApi(idApi, enviada));
                } else {
                    atualizadas.add(c);
                }
            }
            if (achou) {
                salvar(atualizadas);
            } else {
                adicionarParaApagar(idApi);
            }
            return achou;
        }
    }

    /** Ver marcarCriadaNaApi. */
    void marcarEnviada(ListaLocal enviada) {
        synchronized (TRAVA) {
            List<ColecaoSalva> atualizadas = new ArrayList<>();
            for (ColecaoSalva c : carregar()) {
                atualizadas.add(c.id == enviada.id && c.idApi != null
                        ? c.naApi(c.idApi, enviada) : c);
            }
            salvar(atualizadas);
        }
    }

    /** A API nao tem mais a lista (ou ela nao e desta pessoa): na proxima, cria de novo. */
    void esquecerIdApi(long id) {
        synchronized (TRAVA) {
            List<ColecaoSalva> atualizadas = new ArrayList<>();
            for (ColecaoSalva c : carregar()) {
                atualizadas.add(c.id == id ? c.naApi(null, null) : c);
            }
            salvar(atualizadas);
        }
    }

    void esquecerTodosIdsApi() {
        synchronized (TRAVA) {
            List<ColecaoSalva> atualizadas = new ArrayList<>();
            for (ColecaoSalva c : carregar()) {
                atualizadas.add(c.naApi(null, null));
            }
            salvar(atualizadas);
        }
    }

    Set<Long> idsParaApagarNaApi() {
        synchronized (TRAVA) {
            Set<Long> ids = new HashSet<>();
            for (String id : prefs.getStringSet(CHAVE_APAGAR_NA_API, new HashSet<>())) {
                ids.add(Long.parseLong(id));
            }
            return ids;
        }
    }

    void marcarApagadaNaApi(long idApi) {
        synchronized (TRAVA) {
            Set<String> ids = new HashSet<>(prefs.getStringSet(CHAVE_APAGAR_NA_API, new HashSet<>()));
            ids.remove(String.valueOf(idApi));
            prefs.edit().putStringSet(CHAVE_APAGAR_NA_API, ids).apply();
        }
    }

    // Chamado sempre de dentro da TRAVA.
    private void adicionarParaApagar(long idApi) {
        // Copia: o Set devolvido pelo SharedPreferences nao pode ser alterado.
        Set<String> ids = new HashSet<>(prefs.getStringSet(CHAVE_APAGAR_NA_API, new HashSet<>()));
        ids.add(String.valueOf(idApi));
        prefs.edit().putStringSet(CHAVE_APAGAR_NA_API, ids).apply();
    }

    /**
     * A capa de uma lista criada pelo usuario e um arquivo proprio do app (ver
     * ImagemLocalUtil); sem apagar aqui, ele ficaria orfao no armazenamento
     * depois que a lista some. As de exemplo usam drawable, entao nao tem
     * arquivo nenhum a apagar.
     */
    private static void excluirArquivoDaCapa(@Nullable String caminhoImagem) {
        if (caminhoImagem == null) {
            return;
        }
        try {
            String caminho = Uri.parse(caminhoImagem).getPath();
            if (caminho != null) {
                new File(caminho).delete();
            }
        } catch (Exception ignorado) {
            // Falha ao limpar o arquivo nao pode impedir a lista de ser excluida.
        }
    }

    private List<ColecaoSalva> carregar() {
        String salvo = prefs.getString(CHAVE_LISTAS, null);
        if (salvo == null) {
            salvar(EXEMPLO);
            return new ArrayList<>(EXEMPLO);
        }
        List<ColecaoSalva> lista = gson.fromJson(salvo, TIPO_LISTA_SALVA);
        return lista == null ? new ArrayList<>() : lista;
    }

    private void salvar(List<ColecaoSalva> listas) {
        prefs.edit().putString(CHAVE_LISTAS, gson.toJson(listas)).apply();
    }

    /**
     * Um id nunca e reaproveitado, nem depois de excluir a lista mais nova.
     * Os produtos de cada lista e o controle do envio para a API sao guardados
     * por esse id: reaproveitar faria uma lista nova herdar o que era de outra.
     */
    private long proximoId(List<ColecaoSalva> atuais) {
        long maior = 0;
        for (ColecaoSalva c : atuais) {
            if (c.id > maior) {
                maior = c.id;
            }
        }
        return Math.max(maior + 1, prefs.getLong(CHAVE_PROXIMO_ID, 0L));
    }

    private static String comparavel(@Nullable String nome) {
        return nome == null ? "" : nome.trim().toLowerCase(Locale.ROOT);
    }

    /** As de exemplo nao tem descricao (null); a tela grava "" quando a pessoa apaga. */
    @Nullable
    private static String semTextoVazio(@Nullable String texto) {
        return texto == null || texto.trim().isEmpty() ? null : texto;
    }

    private List<Colecao> paraColecoes(List<ColecaoSalva> salvas) {
        List<Colecao> lista = new ArrayList<>();
        for (ColecaoSalva s : salvas) {
            lista.add(s.paraColecao());
        }
        return lista;
    }

    /**
     * Retrato de uma lista para a SincronizacaoListas, sem nada da tela. Os
     * campos "NaApi" sao o que foi mandado da ultima vez; o que difere do
     * valor atual ainda falta enviar.
     */
    static final class ListaLocal {
        final long id;
        final String nome;
        // null quando a lista nao tem descricao, nunca "".
        @Nullable final String descricao;
        // So as 3 listas de exemplo tem chave (favoritos, escaneados, skincare).
        @Nullable final String chaveImagem;
        @Nullable final Long idApi;
        @Nullable final String nomeNaApi;
        @Nullable final String descricaoNaApi;
        @Nullable final String capaNaApi;

        ListaLocal(long id, String nome, @Nullable String descricao, @Nullable String chaveImagem,
                   @Nullable Long idApi, @Nullable String nomeNaApi,
                   @Nullable String descricaoNaApi, @Nullable String capaNaApi) {
            this.id = id;
            this.nome = nome;
            this.descricao = descricao;
            this.chaveImagem = chaveImagem;
            this.idApi = idApi;
            this.nomeNaApi = nomeNaApi;
            this.descricaoNaApi = descricaoNaApi;
            this.capaNaApi = capaNaApi;
        }
    }

    /**
     * Formato gravado no SharedPreferences. Guarda a CHAVE do drawable de
     * exemplo, nao o id do resource: um resource id pode mudar entre builds,
     * e um valor salvo assim ficaria apontando pro drawable errado.
     *
     * idApi e os campos "NaApi" ficam null ate a lista chegar na API - e em
     * quem salvou antes desta versao do app, ja que o Gson deixa null o campo
     * que nao estava no texto salvo. Uma lista que subiu antes de a API ter
     * descricao e capa padrao fica com esses dois null e recebe os dois depois.
     */
    private static class ColecaoSalva {
        final long id;
        final String nome;
        final String descricao;
        final String chaveImagem;
        final String caminhoImagem;
        final Long idApi;
        final String nomeNaApi;
        final String descricaoNaApi;
        final String capaNaApi;

        ColecaoSalva(long id, String nome, String descricao, String chaveImagem,
                     String caminhoImagem) {
            this(id, nome, descricao, chaveImagem, caminhoImagem, null, null, null, null);
        }

        ColecaoSalva(long id, String nome, String descricao, String chaveImagem,
                     String caminhoImagem, Long idApi, String nomeNaApi,
                     String descricaoNaApi, String capaNaApi) {
            this.id = id;
            this.nome = nome;
            this.descricao = descricao;
            this.chaveImagem = chaveImagem;
            this.caminhoImagem = caminhoImagem;
            this.idApi = idApi;
            this.nomeNaApi = nomeNaApi;
            this.descricaoNaApi = descricaoNaApi;
            this.capaNaApi = capaNaApi;
        }

        ColecaoSalva comNome(String novoNome) {
            return new ColecaoSalva(id, novoNome, descricao, chaveImagem, caminhoImagem,
                    idApi, nomeNaApi, descricaoNaApi, capaNaApi);
        }

        ColecaoSalva comDescricao(String novaDescricao) {
            return new ColecaoSalva(id, nome, novaDescricao, chaveImagem, caminhoImagem,
                    idApi, nomeNaApi, descricaoNaApi, capaNaApi);
        }

        ColecaoSalva comImagem(String novoCaminho) {
            return new ColecaoSalva(id, nome, descricao, chaveImagem, novoCaminho,
                    idApi, nomeNaApi, descricaoNaApi, capaNaApi);
        }

        /** @param enviada null esquece tudo o que foi mandado (junto com o id). */
        ColecaoSalva naApi(@Nullable Long novoIdApi, @Nullable ListaLocal enviada) {
            if (enviada == null) {
                return new ColecaoSalva(id, nome, descricao, chaveImagem, caminhoImagem,
                        novoIdApi, null, null, null);
            }
            return new ColecaoSalva(id, nome, descricao, chaveImagem, caminhoImagem,
                    novoIdApi, enviada.nome, enviada.descricao, enviada.chaveImagem);
        }

        Colecao paraColecao() {
            // Uma capa escolhida pelo usuario sempre vence o drawable de
            // exemplo - e como uma lista de exemplo troca de capa.
            if (caminhoImagem != null) {
                return new Colecao(id, nome, caminhoImagem, 0, descricao);
            }
            return new Colecao(id, nome, null, drawablePara(chaveImagem), descricao);
        }

        @DrawableRes
        private static int drawablePara(String chave) {
            if (chave == null) {
                return 0;
            }
            switch (chave) {
                case "favoritos":
                    return R.drawable.capa_favoritos;
                case "escaneados":
                    return R.drawable.capa_escaneados;
                case "skincare":
                    return R.drawable.capa_skincare;
                default:
                    return 0;
            }
        }
    }
}
