package com.venussystem.venusmobile.repository;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.venussystem.venusmobile.repository.ColecaoRepository.ListaLocal;
import com.venussystem.venusmobile.repository.api.ClienteApi;
import com.venussystem.venusmobile.repository.api.VenusApi;
import com.venussystem.venusmobile.repository.api.dto.FatiaResponse;
import com.venussystem.venusmobile.repository.api.dto.UserListItemRequest;
import com.venussystem.venusmobile.repository.api.dto.UserListItemResponse;
import com.venussystem.venusmobile.repository.api.dto.UserListRequest;
import com.venussystem.venusmobile.repository.api.dto.UserListResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import retrofit2.Response;

/**
 * Leva as listas do aparelho para o Venus-CRUD: cria, renomeia e apaga as
 * listas em /api/user-lists e deixa os produtos de cada uma iguais em
 * /api/user-list-items.
 *
 * Como o SincronizacaoRepository, roda quieto e o aparelho continua sendo a
 * fonte do que a tela mostra; o que falhar fica pendente e vai na proxima.
 * Descricao e capa ainda nao vao: a API nao tem onde guardar.
 *
 * So mexe nas listas que o proprio app criou na API. Uma lista feita pelo
 * site, que o app nunca mostrou, fica como esta.
 */
public class SincronizacaoListas {

    private static final String TIPO_PERSONALIZADA = "CUSTOM";
    private static final String TIPO_FAVORITOS = "FAVORITES";
    private static final String TIPO_ROTINA = "ROUTINE";

    private static final int TAMANHO_PAGINA = 100;
    private static final int LIMITE_PAGINAS = 20;

    private static final int PROIBIDO = 403;
    private static final int NAO_ENCONTRADO = 404;
    private static final int CONFLITO = 409;
    private static final int INVALIDO = 422;

    private final ColecaoRepository listas;
    private final ListaItemRepository itens;
    private final UsuarioApiRepository usuarios;
    private final VenusApi api;

    public SincronizacaoListas(Context context) {
        this(new ColecaoRepository(context), new ListaItemRepository(context),
                new UsuarioApiRepository(context), ClienteApi.get());
    }

    @VisibleForTesting
    public SincronizacaoListas(ColecaoRepository listas, ListaItemRepository itens,
                               UsuarioApiRepository usuarios, VenusApi api) {
        this.listas = listas;
        this.itens = itens;
        this.usuarios = usuarios;
        this.api = api;
    }

    /** Mesma fila do envio do perfil: nada de dois envios em paralelo. */
    public void sincronizarEmSegundoPlano() {
        SincronizacaoRepository.EXECUTOR.execute(this::sincronizarAgora);
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
        Long userId = usuarios.obterIdSincrono();
        if (userId == null) {
            return false;
        }

        try {
            boolean tudoEnviado = apagarExcluidas();
            for (ListaLocal lista : listas.paraSincronizar()) {
                tudoEnviado &= sincronizar(userId, lista);
            }
            return tudoEnviado;
        } catch (IOException e) {
            return false;
        } catch (Proibido e) {
            // A API disse que o id nao e desta pessoa. O caso conhecido e o
            // banco recriado: o id guardado da pessoa e os das listas passaram
            // a ser de outras contas. Esquecer tudo faz a proxima tentativa
            // achar a pessoa de novo e recriar as listas.
            usuarios.esquecerId();
            listas.esquecerTodosIdsApi();
            return false;
        }
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
            } else if (!lista.nome.equals(lista.nomeNaApi) || itens.temItensParaEnviar(lista.id)) {
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
            if (!listas.marcarCriadaNaApi(lista.id, idApi, lista.nome)) {
                // Excluida no aparelho enquanto era criada: ja esta na fila de apagar.
                return true;
            }
            // Lista recem-criada vai com os produtos que ja tinha.
            return sincronizarItens(lista.id, idApi);
        }

        if (!lista.nome.equals(lista.nomeNaApi) && !renomear(userId, lista)) {
            return false;
        }
        if (itens.temItensParaEnviar(lista.id)) {
            return sincronizarItens(lista.id, lista.idApi);
        }
        return true;
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
        // A lista ja ligada a outra lista do aparelho nao pode ser assumida
        // de novo: as duas ficariam brigando pelos mesmos produtos.
        Set<Long> jaLigadas = new HashSet<>();
        for (ListaLocal outra : listas.paraSincronizar()) {
            if (outra.idApi != null) {
                jaLigadas.add(outra.idApi);
            }
        }

        for (int pagina = 0; pagina < LIMITE_PAGINAS; pagina++) {
            Response<FatiaResponse<UserListResponse>> resposta =
                    api.listasDoUsuario(userId, pagina, TAMANHO_PAGINA).execute();
            exigirPermissao(resposta);
            FatiaResponse<UserListResponse> fatia = resposta.body();
            if (!resposta.isSuccessful() || fatia == null || fatia.content == null) {
                return null;
            }
            for (UserListResponse daApi : fatia.content) {
                if (daApi.id != null && nome.equals(daApi.name) && !jaLigadas.contains(daApi.id)) {
                    return daApi.id;
                }
            }
            if (!Boolean.FALSE.equals(fatia.last) || fatia.content.size() < TAMANHO_PAGINA) {
                return null;
            }
        }
        return null;
    }

    private boolean renomear(long userId, ListaLocal lista) throws IOException, Proibido {
        UserListRequest corpo = new UserListRequest();
        corpo.userId = userId;
        corpo.name = lista.nome;

        Response<Void> resposta = api.renomearLista(lista.idApi, corpo).execute();
        exigirPermissao(resposta);
        if (resposta.isSuccessful()) {
            listas.marcarNomeEnviado(lista.id, lista.nome);
            return true;
        }
        if (resposta.code() == NAO_ENCONTRADO) {
            // Apagada na API: na proxima vez ela e criada de novo, com os produtos.
            listas.esquecerIdApi(lista.id);
        }
        // 409: o nome ja e de uma lista do site. Fica pendente ate a pessoa
        // trocar o nome aqui ou la.
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
            listas.esquecerIdApi(listaId);
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
            if (!sucesso(codigo) && codigo != NAO_ENCONTRADO && codigo != INVALIDO) {
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
