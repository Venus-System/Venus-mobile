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

public class SincronizacaoListas {
    public interface PreparoDaCapa {
        File preparar(File original) throws IOException;
    }

    private static final String TIPO_PERSONALIZADA = "CUSTOM";
    private static final String TIPO_FAVORITOS = "FAVORITES";
    private static final String TIPO_ROTINA = "ROUTINE";

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

    public void sincronizarEmSegundoPlano() {
        SincronizacaoRepository.EXECUTOR.execute(this::sincronizarAgora);
    }

    public void atualizarEmSegundoPlano(@Nullable Runnable aoMudar) {
        SincronizacaoRepository.EXECUTOR.execute(() -> {
            if (atualizarAgora() && aoMudar != null) {
                principal.execute(aoMudar);
            }
        });
    }

    @WorkerThread
    @VisibleForTesting
    public boolean sincronizarAgora() {
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

    @WorkerThread
    @VisibleForTesting
    public boolean atualizarAgora() {
        Long userId = idDaPessoa();
        if (userId == null) {
            return false;
        }
        try {
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
                return true;
            }
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
        return false;
    }

    private boolean sincronizarItens(long listaId, long idApi) throws IOException, Proibido {
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
            if (!sucesso(codigo) && codigo != NAO_ENCONTRADO && codigo != NAO_PROCESSAVEL) {
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

    private boolean enviarCapa(long idApi, ListaLocal lista) throws IOException, Proibido {
        File original = ImagemReduzida.arquivoLocal(lista.caminhoImagem);
        if (original == null || !original.exists()) {
            listas.marcarCapaEnviada(lista.id, lista.caminhoImagem, null);
            return true;
        }
        File arquivo;
        try {
            arquivo = preparoDaCapa.preparar(original);
        } catch (IOException | RuntimeException e) {
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

    private void sumiuDaApi(long listaId) {
        listas.removerApagadaNaApi(listaId);
        itens.excluirTodos(listaId);
    }

    private boolean trazerDaApi(long userId) throws IOException, Proibido {
        List<UserListResponse> respostas = todasAsListas(userId);
        if (respostas == null) {
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
                break;
            }
        }
        return mudou;
    }

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

    private static String tipoNaApi(@Nullable String chaveImagem) {
        if ("favoritos".equals(chaveImagem)) {
            return TIPO_FAVORITOS;
        }
        if ("skincare".equals(chaveImagem)) {
            return TIPO_ROTINA;
        }
        return TIPO_PERSONALIZADA;
    }

    @Nullable
    private static String capaPadraoNaApi(@Nullable String chaveImagem) {
        return chaveImagem != null && CAPAS_PADRAO.contains(chaveImagem)
                ? chaveImagem.toUpperCase(Locale.ROOT) : null;
    }

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
