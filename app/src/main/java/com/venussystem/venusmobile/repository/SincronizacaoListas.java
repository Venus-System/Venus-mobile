package com.venussystem.venusmobile.repository;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.core.content.ContextCompat;

import com.venussystem.venusmobile.repository.ColecaoRepository.DaApiAplicada;
import com.venussystem.venusmobile.repository.ColecaoRepository.ListaDaApi;
import com.venussystem.venusmobile.repository.ColecaoRepository.ListaLocal;
import com.venussystem.venusmobile.repository.api.ClienteApi;
import com.venussystem.venusmobile.repository.api.VenusApi;
import com.venussystem.venusmobile.repository.api.dto.FatiaResponse;
import com.venussystem.venusmobile.repository.api.dto.MediaAssetResponse;
import com.venussystem.venusmobile.repository.api.dto.UserListItemRequest;
import com.venussystem.venusmobile.repository.api.dto.UserListItemResponse;
import com.venussystem.venusmobile.repository.api.dto.UserListPatchRequest;
import com.venussystem.venusmobile.repository.api.dto.UserListRequest;
import com.venussystem.venusmobile.repository.api.dto.UserListResponse;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Response;

/**
 * Mantem as listas do aparelho e as do Venus-CRUD iguais.
 *
 * Mandar: cria, atualiza (nome, descricao, capa padrao e foto de capa) e
 * apaga as listas em /api/user-lists e deixa os produtos de cada uma iguais
 * em /api/user-list-items. Roda quieto quando uma tela de listas fecha; o que
 * falhar fica pendente e vai na proxima.
 *
 * Trazer: ao abrir o app e a aba Listas, depois de mandar o pendente, le as
 * listas da pessoa no servidor. O servidor vence, menos no que mudou no
 * aparelho e ainda nao subiu (ver ColecaoRepository.aplicarDaApi). Assim a
 * pessoa ve, em outro celular ou depois de reinstalar, as listas que ja tinha,
 * e o que mudou pelo site.
 */
public class SincronizacaoListas {

    /** Prepara o arquivo da capa para o envio. */
    public interface PreparoDaCapa {
        /** @return o arquivo a mandar; se for outro que nao o original, e apagado depois. */
        File preparar(File original) throws IOException;
    }

    private static final String TIPO_PERSONALIZADA = "CUSTOM";
    private static final String TIPO_FAVORITOS = "FAVORITES";
    private static final String TIPO_ROTINA = "ROUTINE";

    // As chaves das listas de exemplo, que na API sao a capa padrao (em maiusculo).
    private static final Set<String> CAPAS_PADRAO =
            new HashSet<>(Arrays.asList("favoritos", "escaneados", "skincare"));

    private static final MediaType JPEG = MediaType.get("image/jpeg");
    private static final String CAMPO_ARQUIVO = "file";

    private static final int TAMANHO_PAGINA = 100;
    private static final int LIMITE_PAGINAS = 20;

    private static final int INVALIDO = 400;
    private static final int PROIBIDO = 403;
    private static final int NAO_ENCONTRADO = 404;
    private static final int CONFLITO = 409;
    private static final int GRANDE_DEMAIS = 413;
    private static final int NAO_PROCESSAVEL = 422;

    private final ColecaoRepository listas;
    private final ListaItemRepository itens;
    private final UsuarioApiRepository usuarios;
    private final VenusApi api;
    private final PreparoDaCapa preparoDaCapa;
    private final Executor principal;

    public SincronizacaoListas(Context context) {
        this(new ColecaoRepository(context), new ListaItemRepository(context),
                new UsuarioApiRepository(context), ClienteApi.get(),
                original -> ImagemReduzida.copiaParaEnvio(context, original),
                ContextCompat.getMainExecutor(context));
    }

    @VisibleForTesting
    public SincronizacaoListas(ColecaoRepository listas, ListaItemRepository itens,
                               UsuarioApiRepository usuarios, VenusApi api,
                               PreparoDaCapa preparoDaCapa, Executor principal) {
        this.listas = listas;
        this.itens = itens;
        this.usuarios = usuarios;
        this.api = api;
        this.preparoDaCapa = preparoDaCapa;
        this.principal = principal;
    }

    /** Mesma fila do envio do perfil: nada de dois envios em paralelo. */
    public void sincronizarEmSegundoPlano() {
        SincronizacaoRepository.EXECUTOR.execute(this::sincronizarAgora);
    }

    /**
     * Manda o pendente e traz o que mudou no servidor.
     *
     * @param aoMudar chamado na thread principal se alguma lista mudou no
     *                aparelho, para a tela recarregar.
     */
    public void atualizarEmSegundoPlano(@Nullable Runnable aoMudar) {
        SincronizacaoRepository.EXECUTOR.execute(() -> {
            if (atualizarAgora() && aoMudar != null) {
                principal.execute(aoMudar);
            }
        });
    }

    /**
     * @return true se tudo o que estava pendente foi para a API.
     */
    @WorkerThread
    @VisibleForTesting
    public boolean sincronizarAgora() {
        // Sem nada para mandar, nem pergunta o id da pessoa: abrir e fechar a
        // tela de uma lista nao pode gastar o cold start do Render a toa.
        if (!temAlgoParaEnviar()) {
            return false;
        }
        Long userId = idDaPessoa();
        if (userId == null) {
            return false;
        }
        try {
            return enviarPendentes(userId);
        } catch (IOException e) {
            return false;
        } catch (Proibido e) {
            esquecerIds();
            return false;
        }
    }

    /**
     * @return true se alguma lista mudou no aparelho.
     */
    @WorkerThread
    @VisibleForTesting
    public boolean atualizarAgora() {
        Long userId = idDaPessoa();
        if (userId == null) {
            return false;
        }
        try {
            // Primeiro o que mudou aqui: assim o que vem do servidor ja inclui.
            enviarPendentes(userId);
            return trazerDaApi(userId);
        } catch (IOException e) {
            return false;
        } catch (Proibido e) {
            esquecerIds();
            return false;
        }
    }

    @Nullable
    private Long idDaPessoa() {
        Long userId = usuarios.obterIdSincrono();
        if (userId != null) {
            listas.conferirDono(userId);
        }
        return userId;
    }

    /**
     * A API disse que o id nao e desta pessoa. O caso conhecido e o banco
     * recriado: o id guardado da pessoa e os das listas passaram a ser de
     * outras contas. Esquecer tudo faz a proxima tentativa achar a pessoa de
     * novo e recriar as listas.
     */
    private void esquecerIds() {
        usuarios.esquecerId();
        listas.esquecerTodosIdsApi();
    }

    private boolean temAlgoParaEnviar() {
        if (!listas.idsParaApagarNaApi().isEmpty()) {
            return true;
        }
        for (ListaLocal lista : listas.paraSincronizar()) {
            if (lista.idApi == null) {
                if (deveExistirNaApi(lista)) {
                    return true;
                }
            } else if (mudouDesdeOEnvio(lista) || itens.temItensParaEnviar(lista.id)
                    || lista.capaPendente()) {
                return true;
            }
        }
        return false;
    }

    /**
     * As 3 listas de exemplo so vao para a API quando ganham o primeiro
     * produto: sem isso, toda conta nova nasceria com 3 listas vazias la. As
     * criadas pela pessoa vao sempre, mesmo vazias - ela pediu para ter.
     */
    private boolean deveExistirNaApi(ListaLocal lista) {
        return lista.chaveImagem == null || !itens.getProdutoIds(lista.id).isEmpty();
    }

    private boolean enviarPendentes(long userId) throws IOException, Proibido {
        boolean tudoEnviado = apagarExcluidas();
        for (ListaLocal lista : listas.paraSincronizar()) {
            tudoEnviado &= sincronizar(userId, lista);
        }
        return tudoEnviado;
    }

    private boolean apagarExcluidas() throws IOException {
        boolean tudoApagado = true;
        for (Long idApi : listas.idsParaApagarNaApi()) {
            int codigo = api.apagarLista(idApi).execute().code();
            // 404: ja nao existe. 403: nao e desta pessoa - nao ha o que apagar.
            if (sucesso(codigo) || codigo == NAO_ENCONTRADO || codigo == PROIBIDO) {
                listas.marcarApagadaNaApi(idApi);
            } else {
                tudoApagado = false;
            }
        }
        return tudoApagado;
    }

    private boolean sincronizar(long userId, ListaLocal lista) throws IOException, Proibido {
        if (lista.idApi == null) {
            if (!deveExistirNaApi(lista)) {
                return true;
            }
            Long idApi = criar(userId, lista);
            if (idApi == null) {
                return false;
            }
            if (!listas.marcarCriadaNaApi(idApi, lista)) {
                // Excluida no aparelho enquanto era criada: ja esta na fila de apagar.
                return true;
            }
            // Lista recem-criada vai com os produtos e a foto que ja tinha.
            if (!sincronizarItens(lista.id, idApi)) {
                return false;
            }
            return !lista.capaPendente() || enviarCapa(idApi, lista);
        }

        if (mudouDesdeOEnvio(lista) && !atualizar(lista)) {
            return false;
        }
        if (itens.temItensParaEnviar(lista.id) && !sincronizarItens(lista.id, lista.idApi)) {
            return false;
        }
        return !lista.capaPendente() || enviarCapa(lista.idApi, lista);
    }

    /**
     * @return o id da lista na API, ou null se nao deu para criar agora.
     */
    @Nullable
    private Long criar(long userId, ListaLocal lista) throws IOException, Proibido {
        UserListRequest corpo = new UserListRequest();
        corpo.userId = userId;
        corpo.name = lista.nome;
        corpo.listType = tipoNaApi(lista.chaveImagem);
        corpo.description = lista.descricao;
        corpo.coverKey = capaPadraoNaApi(lista.chaveImagem);

        Response<UserListResponse> resposta = api.criarLista(corpo).execute();
        exigirPermissao(resposta);
        if (resposta.isSuccessful() && resposta.body() != null && resposta.body().id != null) {
            return resposta.body().id;
        }
        if (resposta.code() == CONFLITO) {
            // A API ja tem uma lista com esse nome. O caso mais comum e uma
            // tentativa anterior que criou a lista mas perdeu a resposta no
            // caminho: assumir a de la evita deixar uma lista duplicada.
            return idDaListaComNome(userId, lista.nome);
        }
        return null;
    }

    @Nullable
    private Long idDaListaComNome(long userId, String nome) throws IOException, Proibido {
        List<UserListResponse> daApi = todasAsListas(userId);
        if (daApi == null) {
            return null;
        }
        // A lista ja ligada a outra lista do aparelho nao pode ser assumida
        // de novo: as duas ficariam brigando pelos mesmos produtos.
        Set<Long> jaLigadas = new HashSet<>();
        for (ListaLocal outra : listas.paraSincronizar()) {
            if (outra.idApi != null) {
                jaLigadas.add(outra.idApi);
            }
        }
        for (UserListResponse lista : daApi) {
            if (lista.id != null && nome.equals(lista.name) && !jaLigadas.contains(lista.id)) {
                return lista.id;
            }
        }
        return null;
    }

    /** Manda so o que mudou desde o ultimo envio. */
    private boolean atualizar(ListaLocal lista) throws IOException, Proibido {
        UserListPatchRequest corpo = new UserListPatchRequest();
        if (!lista.nome.equals(lista.nomeNaApi)) {
            corpo.name(lista.nome);
        }
        if (!Objects.equals(lista.descricao, lista.descricaoNaApi)) {
            corpo.description(lista.descricao);
        }
        if (!Objects.equals(lista.chaveImagem, lista.capaNaApi)) {
            corpo.coverKey(capaPadraoNaApi(lista.chaveImagem));
        }

        Response<Void> resposta = api.atualizarLista(lista.idApi, corpo.paraEnviar()).execute();
        exigirPermissao(resposta);
        if (resposta.isSuccessful()) {
            listas.marcarEnviada(lista);
            return true;
        }
        if (resposta.code() == NAO_ENCONTRADO) {
            sumiuDaApi(lista.id);
        }
        // 409: o nome ja e de uma lista do site. Fica pendente (com a
        // descricao, se ela mudou junto) ate a pessoa trocar o nome aqui ou la.
        return false;
    }

    /**
     * Deixa os produtos da lista na API iguais aos do aparelho.
     *
     * A posicao de um produto novo e sempre a maior da lista + 1, e nunca e
     * reaproveitada: tirar um produto deixa um buraco, o que a API aceita (ela
     * so nao aceita posicao repetida). Assim a ordem da API, da maior posicao
     * para a menor, e a mesma da tela, do mais recente para o mais antigo.
     */
    private boolean sincronizarItens(long listaId, long idApi) throws IOException, Proibido {
        // A versao e lida antes dos produtos: uma mudanca no meio do envio
        // sobe a versao e continua pendente.
        long versao = itens.versao(listaId);
        List<Long> doAparelho = itens.getProdutoIds(listaId);

        Response<List<UserListItemResponse>> resposta = api.itensDaLista(idApi).execute();
        exigirPermissao(resposta);
        if (resposta.code() == NAO_ENCONTRADO) {
            sumiuDaApi(listaId);
            return false;
        }
        if (!resposta.isSuccessful() || resposta.body() == null) {
            return false;
        }

        Map<Long, Integer> naApi = new HashMap<>();
        int maiorPosicao = 0;
        for (UserListItemResponse item : resposta.body()) {
            if (item.productId == null) {
                continue;
            }
            int posicao = item.positionOrder == null ? 0 : item.positionOrder;
            naApi.put(item.productId, posicao);
            maiorPosicao = Math.max(maiorPosicao, posicao);
        }

        boolean tudoEnviado = true;

        // Do mais antigo para o mais recente, para o mais recente ficar com a
        // maior posicao.
        for (int i = doAparelho.size() - 1; i >= 0; i--) {
            Long produtoId = doAparelho.get(i);
            if (naApi.containsKey(produtoId)) {
                continue;
            }
            UserListItemRequest corpo = new UserListItemRequest();
            corpo.userListId = idApi;
            corpo.productId = produtoId;
            corpo.positionOrder = ++maiorPosicao;

            Response<Void> adicionado = api.adicionarItem(corpo).execute();
            exigirPermissao(adicionado);
            int codigo = adicionado.code();
            // 404/422: o produto saiu do catalogo. Insistir nao adianta, e o
            // resto da lista nao pode ficar parado por causa dele.
            if (!sucesso(codigo) && codigo != NAO_ENCONTRADO && codigo != NAO_PROCESSAVEL) {
                // 409: outro envio usou a posicao ao mesmo tempo. Na proxima,
                // a posicao e recalculada com a lista lida de novo.
                tudoEnviado = false;
            }
        }

        for (Long produtoId : naApi.keySet()) {
            if (doAparelho.contains(produtoId)) {
                continue;
            }
            Response<Void> removido = api.removerItem(idApi, produtoId).execute();
            exigirPermissao(removido);
            if (!removido.isSuccessful() && removido.code() != NAO_ENCONTRADO) {
                tudoEnviado = false;
            }
        }

        if (tudoEnviado) {
            itens.marcarItensEnviados(listaId, versao);
        }
        return tudoEnviado;
    }

    /**
     * Manda a foto de capa escolhida no aparelho. A original fica guardada
     * inteira para a tela; vai uma copia reduzida (ver ImagemReduzida), que e
     * apagada depois do envio.
     */
    private boolean enviarCapa(long idApi, ListaLocal lista) throws IOException, Proibido {
        File original = ImagemReduzida.arquivoLocal(lista.caminhoImagem);
        if (original == null || !original.exists()) {
            // O arquivo sumiu (limpeza do sistema): nao ha o que mandar.
            listas.marcarCapaEnviada(lista.id, lista.caminhoImagem, null);
            return true;
        }
        File arquivo;
        try {
            arquivo = preparoDaCapa.preparar(original);
        } catch (IOException | RuntimeException e) {
            // Imagem corrompida ou num formato que o Android nao abre: fica so aqui.
            listas.marcarCapaEnviada(lista.id, lista.caminhoImagem, null);
            return true;
        }

        try {
            RequestBody corpo = RequestBody.create(arquivo, JPEG);
            MultipartBody.Part parte =
                    MultipartBody.Part.createFormData(CAMPO_ARQUIVO, arquivo.getName(), corpo);
            Response<MediaAssetResponse> resposta = api.enviarCapaDaLista(idApi, parte).execute();
            exigirPermissao(resposta);
            int codigo = resposta.code();

            if (resposta.isSuccessful()) {
                MediaAssetResponse capa = resposta.body();
                listas.marcarCapaEnviada(lista.id, lista.caminhoImagem,
                        capa == null ? null : capa.url);
                return true;
            }
            if (codigo == INVALIDO || codigo == GRANDE_DEMAIS || codigo == NAO_PROCESSAVEL) {
                // A API recusou esta imagem (tipo, tamanho ou dimensao). Mandar
                // a mesma de novo nao adianta: ela fica so no aparelho.
                listas.marcarCapaEnviada(lista.id, lista.caminhoImagem, null);
                return true;
            }
            if (codigo == NAO_ENCONTRADO) {
                sumiuDaApi(lista.id);
            }
            return false;
        } finally {
            if (!arquivo.equals(original)) {
                arquivo.delete();
            }
        }
    }

    /** A lista foi apagada no servidor: sai do aparelho tambem, sem voltar para la. */
    private void sumiuDaApi(long listaId) {
        listas.removerApagadaNaApi(listaId);
        itens.excluirTodos(listaId);
    }

    // ---- Trazer do servidor ----

    /**
     * @return true se alguma lista mudou no aparelho.
     */
    private boolean trazerDaApi(long userId) throws IOException, Proibido {
        List<UserListResponse> respostas = todasAsListas(userId);
        if (respostas == null) {
            // Sem a lista inteira, nao da para saber o que foi apagado la.
            return false;
        }
        List<ListaDaApi> daApi = new ArrayList<>();
        for (UserListResponse resposta : respostas) {
            if (resposta.id != null && resposta.name != null) {
                daApi.add(new ListaDaApi(resposta.id, resposta.name, resposta.description,
                        chaveDaCapaPadrao(resposta.coverKey), resposta.coverUrl));
            }
        }

        DaApiAplicada aplicada = listas.aplicarDaApi(daApi);
        boolean mudou = aplicada.mudou;
        for (long listaId : aplicada.removidas) {
            itens.excluirTodos(listaId);
        }
        for (Map.Entry<Long, Long> ligada : aplicada.ligadas.entrySet()) {
            try {
                mudou |= trazerItens(ligada.getKey(), ligada.getValue(),
                        aplicada.juntadas.contains(ligada.getValue()));
            } catch (IOException e) {
                // Sem rede no meio: o resto vem na proxima. O que ja mudou fica.
                break;
            }
        }
        return mudou;
    }

    /**
     * Traz os produtos de uma lista, do mais recente (maior posicao) para o
     * mais antigo, como na tela. Produtos mudados aqui e ainda nao enviados
     * ficam: o proximo envio manda.
     *
     * @param juntada a lista do aparelho que ainda nao tinha subido e virou
     *                esta do servidor: fica com os produtos dos dois.
     */
    private boolean trazerItens(long idApi, long listaId, boolean juntada)
            throws IOException, Proibido {
        long versao = itens.versao(listaId);
        boolean juntar = juntada && !itens.getProdutoIds(listaId).isEmpty();
        if (!juntar && itens.temItensParaEnviar(listaId)) {
            return false;
        }

        Response<List<UserListItemResponse>> resposta = api.itensDaLista(idApi).execute();
        exigirPermissao(resposta);
        if (!resposta.isSuccessful() || resposta.body() == null) {
            return false;
        }
        List<UserListItemResponse> daApi = new ArrayList<>();
        for (UserListItemResponse item : resposta.body()) {
            if (item.productId != null) {
                daApi.add(item);
            }
        }
        daApi.sort((a, b) -> Integer.compare(posicao(b), posicao(a)));
        List<Long> produtos = new ArrayList<>();
        for (UserListItemResponse item : daApi) {
            produtos.add(item.productId);
        }

        return juntar
                ? itens.juntarComOsDaApi(listaId, produtos)
                : itens.trocarPelosDaApi(listaId, versao, produtos);
    }

    /**
     * Todas as listas da pessoa na API, pagina por pagina.
     *
     * @return null se alguma pagina falhou ou se passou do limite de paginas.
     */
    @Nullable
    private List<UserListResponse> todasAsListas(long userId) throws IOException, Proibido {
        List<UserListResponse> todas = new ArrayList<>();
        for (int pagina = 0; pagina < LIMITE_PAGINAS; pagina++) {
            Response<FatiaResponse<UserListResponse>> resposta =
                    api.listasDoUsuario(userId, pagina, TAMANHO_PAGINA).execute();
            exigirPermissao(resposta);
            FatiaResponse<UserListResponse> fatia = resposta.body();
            if (!resposta.isSuccessful() || fatia == null || fatia.content == null) {
                return null;
            }
            todas.addAll(fatia.content);
            if (!Boolean.FALSE.equals(fatia.last) || fatia.content.size() < TAMANHO_PAGINA) {
                return todas;
            }
        }
        return null;
    }

    private static int posicao(UserListItemResponse item) {
        return item.positionOrder == null ? 0 : item.positionOrder;
    }

    private static boolean mudouDesdeOEnvio(ListaLocal lista) {
        return !lista.nome.equals(lista.nomeNaApi)
                || !Objects.equals(lista.descricao, lista.descricaoNaApi)
                || !Objects.equals(lista.chaveImagem, lista.capaNaApi);
    }

    /**
     * Tipo da lista na API. As de exemplo tem tipo proprio quando existe um
     * que combina; o resto, inclusive "Produtos escaneados", e personalizada.
     */
    private static String tipoNaApi(@Nullable String chaveImagem) {
        if ("favoritos".equals(chaveImagem)) {
            return TIPO_FAVORITOS;
        }
        if ("skincare".equals(chaveImagem)) {
            return TIPO_ROTINA;
        }
        return TIPO_PERSONALIZADA;
    }

    /**
     * Capa padrao na API: a mesma chave das listas de exemplo, em maiusculo
     * (a API recusa minusculo). As outras listas nao tem.
     */
    @Nullable
    private static String capaPadraoNaApi(@Nullable String chaveImagem) {
        return chaveImagem != null && CAPAS_PADRAO.contains(chaveImagem)
                ? chaveImagem.toUpperCase(Locale.ROOT) : null;
    }

    /** O contrario de capaPadraoNaApi; null para valor que o app nao conhece. */
    @Nullable
    private static String chaveDaCapaPadrao(@Nullable String coverKey) {
        if (coverKey == null) {
            return null;
        }
        String chave = coverKey.toLowerCase(Locale.ROOT);
        return CAPAS_PADRAO.contains(chave) ? chave : null;
    }

    private static void exigirPermissao(Response<?> resposta) throws Proibido {
        if (resposta.code() == PROIBIDO) {
            throw new Proibido();
        }
    }

    private static boolean sucesso(int codigo) {
        return codigo >= 200 && codigo < 300;
    }

    private static class Proibido extends Exception {
    }
}
