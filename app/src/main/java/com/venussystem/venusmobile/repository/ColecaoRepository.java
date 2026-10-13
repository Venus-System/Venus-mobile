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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Listas do proprio usuario, guardadas no aparelho, separadas por conta (ver
 * DadosDaConta): quem entra com outro e-mail no mesmo celular nao ve as listas
 * da conta anterior.
 *
 * A tela mostra o que esta guardado aqui. A SincronizacaoListas manda para
 * /api/user-lists o que mudou no aparelho e traz de la o que mudou no
 * servidor. Para saber o que falta mandar, cada lista guarda o id da API e o
 * ultimo nome, descricao, capa padrao e foto de capa enviados.
 *
 * A conta comeca sem nenhuma lista: so existe o que a pessoa criou (aqui ou
 * no site).
 */
public class ColecaoRepository {

    static final String ARQUIVO = "venus_listas";
    private static final String CHAVE_LISTAS = "minhas_listas";
    private static final String CHAVE_PROXIMO_ID = "proximo_id";
    private static final String CHAVE_APAGAR_NA_API = "apagar_na_api";
    private static final String CHAVE_DONO_NA_API = "dono_na_api";
    @VisibleForTesting
    static final String CHAVE_EXEMPLOS_REVISTOS = "exemplos_revistos";

    // Ate esta versao, toda conta nascia com estas 3 listas (chave da capa ->
    // nome). Sao usadas so para achar as que ficaram como nasceram e tirar.
    private static final Map<String, String> EXEMPLOS_ANTIGOS = new HashMap<>();

    static {
        EXEMPLOS_ANTIGOS.put("favoritos", "Produtos favoritados");
        EXEMPLOS_ANTIGOS.put("escaneados", "Produtos escaneados");
        EXEMPLOS_ANTIGOS.put("skincare", "Rotina de skincare");
    }

    private static final Type TIPO_LISTA_SALVA = new TypeToken<List<ColecaoSalva>>() {
    }.getType();

    // A tela e a sincronizacao (em outra thread) leem e regravam a mesma lista
    // de listas. Sem a trava, uma gravacao de uma apagava a da outra.
    private static final Object TRAVA = new Object();

    private final SharedPreferences prefs;
    // So para saber se uma lista de exemplo antiga ganhou produto.
    private final ListaItemRepository itens;
    private final Gson gson = new Gson();

    public ColecaoRepository(Context context) {
        this(context, DadosDaConta.uidAtual(context));
    }

    @VisibleForTesting
    public ColecaoRepository(Context context, @Nullable String uid) {
        this.prefs = DadosDaConta.prefs(context, ARQUIVO, uid);
        this.itens = new ListaItemRepository(context, uid);
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
     * Troca a capa de uma lista ja existente (a de exemplo ou uma criada) e
     * apaga o arquivo da anterior. Silenciosamente nao faz nada se o id nao
     * existir mais - a tela que chamou ja teria fechado nesse caso.
     */
    public void atualizarImagem(long id, String caminhoImagem) {
        synchronized (TRAVA) {
            List<ColecaoSalva> atualizadas = new ArrayList<>();
            for (ColecaoSalva c : carregar()) {
                if (c.id == id) {
                    if (!caminhoImagem.equals(c.caminhoImagem)) {
                        excluirArquivoDaCapa(c.caminhoImagem);
                    }
                    atualizadas.add(c.comImagem(caminhoImagem));
                } else {
                    atualizadas.add(c);
                }
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
                        c.caminhoImagem, c.idApi, c.nomeNaApi, c.descricaoNaApi, c.capaNaApi,
                        c.capaEnviada));
            }
            return listas;
        }
    }

    /**
     * Os ids da API guardados aqui sao da pessoa com esse id. Se ele mudou, o
     * banco foi recriado e a conta ganhou outro id: os ids antigos nao valem
     * mais. Esquecer faz as listas subirem de novo para a conta nova - sem
     * isso, elas sairiam do aparelho por nao estarem nela.
     */
    void conferirDono(long userId) {
        synchronized (TRAVA) {
            long anterior = prefs.getLong(CHAVE_DONO_NA_API, -1L);
            if (anterior != -1L && anterior != userId) {
                esquecerTodosIdsApi();
                // As da fila sao do banco antigo: nao ha mais o que apagar.
                prefs.edit().remove(CHAVE_APAGAR_NA_API).apply();
            }
            prefs.edit().putLong(CHAVE_DONO_NA_API, userId).apply();
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

    /**
     * A foto de capa nesse caminho ja foi tratada: subiu ou a API recusou.
     * Uma foto trocada no meio do envio continua pendente.
     *
     * @param linkNaApi o link que a API deu para a foto; null mantem o que
     *                  estava (a API recusou e ficou com a anterior).
     */
    void marcarCapaEnviada(long id, String caminhoEnviado, @Nullable String linkNaApi) {
        synchronized (TRAVA) {
            List<ColecaoSalva> atualizadas = new ArrayList<>();
            for (ColecaoSalva c : carregar()) {
                atualizadas.add(c.id == id ? c.comCapaEnviada(caminhoEnviado, linkNaApi) : c);
            }
            salvar(atualizadas);
        }
    }

    /**
     * Ids que nao valem mais (o 403 de banco recriado): na proxima, as listas
     * sao criadas de novo, com os produtos e a foto.
     */
    void esquecerTodosIdsApi() {
        synchronized (TRAVA) {
            List<ColecaoSalva> atualizadas = new ArrayList<>();
            for (ColecaoSalva c : carregar()) {
                atualizadas.add(c.naApi(null, null));
            }
            salvar(atualizadas);
        }
    }

    /**
     * A lista foi apagada no servidor (pelo site ou por outro aparelho): sai
     * daqui tambem, sem entrar na fila de apagar.
     */
    void removerApagadaNaApi(long id) {
        synchronized (TRAVA) {
            List<ColecaoSalva> atualizadas = new ArrayList<>();
            for (ColecaoSalva c : carregar()) {
                if (c.id == id) {
                    excluirArquivoDaCapa(c.caminhoImagem);
                } else {
                    atualizadas.add(c);
                }
            }
            salvar(atualizadas);
        }
    }

    /**
     * Deixa as listas do aparelho iguais as do servidor, menos o que mudou
     * aqui e ainda nao subiu (ver ColecaoSalva.comDadosDaApi).
     *
     * - A que o aparelho tem e o servidor nao: foi apagada la e sai daqui.
     * - A que so o servidor tem: se e uma do aparelho que ainda nao tinha
     *   subido (pela capa padrao ou pelo nome), as duas viram uma; senao,
     *   entra no topo.
     * - A que esta na fila de apagar nao volta.
     *
     * @param daApi todas as listas da pessoa na API. Uma lista incompleta
     *              apagaria daqui as que faltassem.
     */
    DaApiAplicada aplicarDaApi(List<ListaDaApi> daApi) {
        synchronized (TRAVA) {
            DaApiAplicada resultado = new DaApiAplicada();
            Map<Long, ListaDaApi> porId = new HashMap<>();
            for (ListaDaApi lista : daApi) {
                porId.put(lista.id, lista);
            }
            Set<Long> naFilaDeApagar = idsParaApagarNaApi();

            List<ColecaoSalva> atualizadas = new ArrayList<>();
            for (ColecaoSalva c : carregar()) {
                if (c.idApi == null) {
                    atualizadas.add(c);
                    continue;
                }
                ListaDaApi doServidor = porId.get(c.idApi);
                if (doServidor == null) {
                    excluirArquivoDaCapa(c.caminhoImagem);
                    resultado.removidas.add(c.id);
                    continue;
                }
                ColecaoSalva nova = c.comDadosDaApi(doServidor);
                trocarArquivoDaCapa(c, nova);
                resultado.mudou |= !c.mesmaNaTela(nova);
                resultado.ligadas.put(doServidor.id, c.id);
                atualizadas.add(nova);
            }

            List<ListaDaApi> novas = new ArrayList<>();
            for (ListaDaApi doServidor : daApi) {
                if (resultado.ligadas.containsKey(doServidor.id)
                        || naFilaDeApagar.contains(doServidor.id)) {
                    continue;
                }
                int posicao = mesmaListaSemId(atualizadas, doServidor);
                if (posicao < 0) {
                    novas.add(doServidor);
                    continue;
                }
                ColecaoSalva c = atualizadas.get(posicao);
                ColecaoSalva nova = c.juntadaCom(doServidor);
                trocarArquivoDaCapa(c, nova);
                atualizadas.set(posicao, nova);
                resultado.ligadas.put(doServidor.id, c.id);
                resultado.juntadas.add(c.id);
                resultado.mudou |= !c.mesmaNaTela(nova);
            }

            // A mais nova primeiro, como as criadas aqui.
            novas.sort((a, b) -> Long.compare(b.id, a.id));
            long proximo = proximoId(atualizadas);
            List<ColecaoSalva> todas = new ArrayList<>();
            for (ListaDaApi doServidor : novas) {
                long id = proximo++;
                todas.add(ColecaoSalva.vindaDaApi(id, doServidor));
                resultado.ligadas.put(doServidor.id, id);
            }
            todas.addAll(atualizadas);
            resultado.mudou |= !novas.isEmpty() || !resultado.removidas.isEmpty();

            prefs.edit()
                    .putString(CHAVE_LISTAS, gson.toJson(todas))
                    .putLong(CHAVE_PROXIMO_ID, proximo)
                    .apply();
            return resultado;
        }
    }

    /** A lista do aparelho ainda sem id que e a mesma do servidor; -1 se nenhuma. */
    private static int mesmaListaSemId(List<ColecaoSalva> listas, ListaDaApi doServidor) {
        if (doServidor.chave != null) {
            for (int i = 0; i < listas.size(); i++) {
                ColecaoSalva c = listas.get(i);
                if (c.idApi == null && doServidor.chave.equals(c.chaveImagem)) {
                    return i;
                }
            }
        }
        String nome = comparavel(doServidor.nome);
        for (int i = 0; i < listas.size(); i++) {
            ColecaoSalva c = listas.get(i);
            if (c.idApi == null && comparavel(c.nome).equals(nome)) {
                return i;
            }
        }
        return -1;
    }

    /** A foto local que deu lugar a do servidor nao serve mais. */
    private static void trocarArquivoDaCapa(ColecaoSalva antes, ColecaoSalva depois) {
        if (!Objects.equals(antes.caminhoImagem, depois.caminhoImagem)) {
            excluirArquivoDaCapa(antes.caminhoImagem);
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
     * depois que a lista some. As de exemplo usam drawable, e a capa que veio
     * do servidor e um link: nenhuma das duas tem arquivo a apagar.
     */
    private static void excluirArquivoDaCapa(@Nullable String caminhoImagem) {
        if (caminhoImagem == null || !caminhoImagem.startsWith("file:")) {
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
        List<ColecaoSalva> lista = salvo == null ? null : gson.fromJson(salvo, TIPO_LISTA_SALVA);
        if (lista == null) {
            lista = new ArrayList<>();
        }
        if (!prefs.getBoolean(CHAVE_EXEMPLOS_REVISTOS, false)) {
            lista = tirarExemplosIntocados(lista);
        }
        return lista;
    }

    /**
     * Roda uma vez por conta: tira as listas de exemplo que ficaram como
     * nasceram - vazias, com o nome e a capa padrao, sem descricao e sem ter
     * subido. Se a pessoa mexeu em alguma, ela fica.
     *
     * Uma vez so porque, depois, uma lista assim pode ser legitima: uma que
     * veio da API com capa padrao e teve o id esquecido (ver esquecerTodosIdsApi).
     *
     * Chamado sempre de dentro da TRAVA.
     */
    private List<ColecaoSalva> tirarExemplosIntocados(List<ColecaoSalva> salvas) {
        List<ColecaoSalva> ficam = new ArrayList<>();
        for (ColecaoSalva c : salvas) {
            if (c.exemploIntocado() && itens.getProdutoIds(c.id).isEmpty()) {
                // Um produto que entrou e saiu deixa o controle de envio para tras.
                itens.excluirTodos(c.id);
            } else {
                ficam.add(c);
            }
        }
        prefs.edit()
                .putString(CHAVE_LISTAS, gson.toJson(ficam))
                // Os ids das que sairam nao voltam (ver proximoId).
                .putLong(CHAVE_PROXIMO_ID, proximoId(salvas))
                .putBoolean(CHAVE_EXEMPLOS_REVISTOS, true)
                .apply();
        return ficam;
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
     * campos "NaApi" e capaEnviada sao o que foi mandado da ultima vez; o que
     * difere do valor atual ainda falta enviar.
     */
    static final class ListaLocal {
        final long id;
        final String nome;
        // null quando a lista nao tem descricao, nunca "".
        @Nullable final String descricao;
        // So tem chave (favoritos, escaneados, skincare) a lista que veio da API
        // com capa padrao ou uma das listas de exemplo antigas que a pessoa usou.
        @Nullable final String chaveImagem;
        // Arquivo da foto escolhida aqui (file:), link da foto do servidor, ou null.
        @Nullable final String caminhoImagem;
        @Nullable final Long idApi;
        @Nullable final String nomeNaApi;
        @Nullable final String descricaoNaApi;
        @Nullable final String capaNaApi;
        @Nullable final String capaEnviada;

        ListaLocal(long id, String nome, @Nullable String descricao, @Nullable String chaveImagem,
                   @Nullable String caminhoImagem, @Nullable Long idApi,
                   @Nullable String nomeNaApi, @Nullable String descricaoNaApi,
                   @Nullable String capaNaApi, @Nullable String capaEnviada) {
            this.id = id;
            this.nome = nome;
            this.descricao = descricao;
            this.chaveImagem = chaveImagem;
            this.caminhoImagem = caminhoImagem;
            this.idApi = idApi;
            this.nomeNaApi = nomeNaApi;
            this.descricaoNaApi = descricaoNaApi;
            this.capaNaApi = capaNaApi;
            this.capaEnviada = capaEnviada;
        }

        /** Uma foto escolhida aqui que ainda nao foi para a API. */
        boolean capaPendente() {
            return fotoPendente(caminhoImagem, capaEnviada);
        }
    }

    /** Uma lista como a API devolve, ja no formato do aparelho. */
    static final class ListaDaApi {
        final long id;
        final String nome;
        @Nullable final String descricao;
        // Em minusculo, como a chaveImagem das listas de exemplo.
        @Nullable final String chave;
        @Nullable final String linkCapa;

        ListaDaApi(long id, String nome, @Nullable String descricao, @Nullable String chave,
                   @Nullable String linkCapa) {
            this.id = id;
            this.nome = nome;
            this.descricao = semTextoVazio(descricao);
            this.chave = chave;
            this.linkCapa = semTextoVazio(linkCapa);
        }
    }

    /** O que aplicarDaApi mudou, para a SincronizacaoListas trazer os produtos. */
    static final class DaApiAplicada {
        /** id na API -> id no aparelho, de toda lista que esta nos dois. */
        final Map<Long, Long> ligadas = new HashMap<>();
        /** Listas do aparelho que ainda nao tinham subido e viraram uma do servidor. */
        final Set<Long> juntadas = new HashSet<>();
        /** Listas que sairam do aparelho porque nao existem mais no servidor. */
        final List<Long> removidas = new ArrayList<>();
        boolean mudou;
    }

    private static boolean fotoPendente(@Nullable String caminhoImagem, @Nullable String capaEnviada) {
        return ImagemReduzida.arquivoLocal(caminhoImagem) != null
                && !caminhoImagem.equals(capaEnviada);
    }

    /**
     * Formato gravado no SharedPreferences. Guarda a CHAVE do drawable de
     * exemplo, nao o id do resource: um resource id pode mudar entre builds,
     * e um valor salvo assim ficaria apontando pro drawable errado.
     *
     * idApi e os campos "NaApi" ficam null ate a lista chegar na API - e em
     * quem salvou antes desta versao do app, ja que o Gson deixa null o campo
     * que nao estava no texto salvo. Uma lista que subiu antes de a API ter
     * descricao e capa padrao fica com esses dois null e recebe os dois depois;
     * uma foto escolhida antes desta versao fica com capaEnviada null e sobe.
     *
     * capaEnviada e o caminho da ultima foto daqui que foi para a API (ou que
     * ela recusou). linkCapaNaApi e o link da foto que o servidor tem: quando
     * ele muda, a foto foi trocada no site ou em outro aparelho.
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
        final String capaEnviada;
        final String linkCapaNaApi;

        ColecaoSalva(long id, String nome, String descricao, String chaveImagem,
                     String caminhoImagem) {
            this(id, nome, descricao, chaveImagem, caminhoImagem,
                    null, null, null, null, null, null);
        }

        ColecaoSalva(long id, String nome, String descricao, String chaveImagem,
                     String caminhoImagem, Long idApi, String nomeNaApi,
                     String descricaoNaApi, String capaNaApi, String capaEnviada,
                     String linkCapaNaApi) {
            this.id = id;
            this.nome = nome;
            this.descricao = descricao;
            this.chaveImagem = chaveImagem;
            this.caminhoImagem = caminhoImagem;
            this.idApi = idApi;
            this.nomeNaApi = nomeNaApi;
            this.descricaoNaApi = descricaoNaApi;
            this.capaNaApi = capaNaApi;
            this.capaEnviada = capaEnviada;
            this.linkCapaNaApi = linkCapaNaApi;
        }

        /** Uma lista que so o servidor tinha. */
        static ColecaoSalva vindaDaApi(long id, ListaDaApi daApi) {
            return new ColecaoSalva(id, daApi.nome, daApi.descricao, daApi.chave, daApi.linkCapa,
                    daApi.id, daApi.nome, daApi.descricao, daApi.chave, null, daApi.linkCapa);
        }

        ColecaoSalva comNome(String novoNome) {
            return new ColecaoSalva(id, novoNome, descricao, chaveImagem, caminhoImagem,
                    idApi, nomeNaApi, descricaoNaApi, capaNaApi, capaEnviada, linkCapaNaApi);
        }

        ColecaoSalva comDescricao(String novaDescricao) {
            return new ColecaoSalva(id, nome, novaDescricao, chaveImagem, caminhoImagem,
                    idApi, nomeNaApi, descricaoNaApi, capaNaApi, capaEnviada, linkCapaNaApi);
        }

        ColecaoSalva comImagem(String novoCaminho) {
            return new ColecaoSalva(id, nome, descricao, chaveImagem, novoCaminho,
                    idApi, nomeNaApi, descricaoNaApi, capaNaApi, capaEnviada, linkCapaNaApi);
        }

        ColecaoSalva comCapaEnviada(String caminhoEnviado, @Nullable String linkNaApi) {
            return new ColecaoSalva(id, nome, descricao, chaveImagem, caminhoImagem,
                    idApi, nomeNaApi, descricaoNaApi, capaNaApi, caminhoEnviado,
                    linkNaApi != null ? linkNaApi : linkCapaNaApi);
        }

        /** @param enviada null esquece tudo o que foi mandado (junto com o id). */
        ColecaoSalva naApi(@Nullable Long novoIdApi, @Nullable ListaLocal enviada) {
            if (enviada == null) {
                return new ColecaoSalva(id, nome, descricao, chaveImagem, caminhoImagem,
                        novoIdApi, null, null, null, null, null);
            }
            return new ColecaoSalva(id, nome, descricao, chaveImagem, caminhoImagem,
                    novoIdApi, enviada.nome, enviada.descricao, enviada.chaveImagem,
                    capaEnviada, linkCapaNaApi);
        }

        /**
         * Pega do servidor cada campo que nao mudou aqui desde o ultimo envio.
         * O que mudou aqui e ainda nao subiu fica: o proximo envio manda.
         */
        ColecaoSalva comDadosDaApi(ListaDaApi daApi) {
            String novoNome = nome;
            String novoNomeNaApi = nomeNaApi;
            if (Objects.equals(nome, nomeNaApi)) {
                novoNome = daApi.nome;
                novoNomeNaApi = daApi.nome;
            }
            String novaDescricao = descricao;
            String novaDescricaoNaApi = descricaoNaApi;
            if (Objects.equals(semTextoVazio(descricao), descricaoNaApi)) {
                novaDescricao = daApi.descricao;
                novaDescricaoNaApi = daApi.descricao;
            }
            String novaChave = chaveImagem;
            String novaCapaNaApi = capaNaApi;
            if (Objects.equals(chaveImagem, capaNaApi)) {
                novaChave = daApi.chave;
                novaCapaNaApi = daApi.chave;
            }
            String novoCaminho = caminhoImagem;
            String novaCapaEnviada = capaEnviada;
            String novoLink = linkCapaNaApi;
            if (!fotoPendente(caminhoImagem, capaEnviada)
                    && !Objects.equals(daApi.linkCapa, linkCapaNaApi)) {
                novoCaminho = daApi.linkCapa;
                novaCapaEnviada = null;
                novoLink = daApi.linkCapa;
            }
            return new ColecaoSalva(id, novoNome, novaDescricao, novaChave, novoCaminho,
                    daApi.id, novoNomeNaApi, novaDescricaoNaApi, novaCapaNaApi,
                    novaCapaEnviada, novoLink);
        }

        /**
         * Esta lista ainda nao tinha subido e e a mesma do servidor: fica com
         * os dados de la. So uma foto escolhida aqui fica, para subir depois.
         */
        ColecaoSalva juntadaCom(ListaDaApi daApi) {
            String caminho = fotoPendente(caminhoImagem, capaEnviada) ? caminhoImagem : daApi.linkCapa;
            return new ColecaoSalva(id, daApi.nome, daApi.descricao, daApi.chave, caminho,
                    daApi.id, daApi.nome, daApi.descricao, daApi.chave, null, daApi.linkCapa);
        }

        /** Uma das 3 listas de exemplo antigas, do jeito que nasceu. */
        boolean exemploIntocado() {
            return chaveImagem != null
                    && nome != null && nome.equals(EXEMPLOS_ANTIGOS.get(chaveImagem))
                    && semTextoVazio(descricao) == null
                    && caminhoImagem == null
                    && idApi == null;
        }

        boolean mesmaNaTela(ColecaoSalva outra) {
            return Objects.equals(nome, outra.nome)
                    && Objects.equals(semTextoVazio(descricao), semTextoVazio(outra.descricao))
                    && Objects.equals(chaveImagem, outra.chaveImagem)
                    && Objects.equals(caminhoImagem, outra.caminhoImagem);
        }

        Colecao paraColecao() {
            // Uma capa com foto sempre vence o drawable de exemplo - e como
            // uma lista de exemplo troca de capa.
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
