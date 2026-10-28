package com.venussystem.venusmobile.repository;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.venussystem.venusmobile.repository.banco.CurtidoDao;
import com.venussystem.venusmobile.repository.banco.CurtidoEntity;
import com.venussystem.venusmobile.repository.banco.VenusDatabase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Os produtos que a pessoa curtiu (o coracao na tela do produto), guardados no
 * banco SQLite do aparelho (tabela curtido do VenusDatabase) e separados por
 * conta, como as listas. A copia em /api/favorites e mantida pela
 * SincronizacaoCurtidos.
 *
 * A ordem guardada e a da tela: o mais recente primeiro.
 *
 * Cada toque deixa o produto pendente de curtir ou de descurtir na API, e so
 * vale a ultima vontade: curtir tira o produto da fila de descurtir e
 * vice-versa. Assim um envio que termina depois de a pessoa mudar de ideia
 * nao apaga a mudanca - ela continua na outra fila e vai no proximo envio.
 */
public class CurtidosRepository {

    // O arquivo de preferencias onde os curtidos ficavam antes do banco. So e
    // lido uma vez, para trazer os curtidos de quem atualizou o app.
    static final String ARQUIVO = "venus_curtidos";

    private static final String CHAVE_PRODUTOS = CurtidoEntity.PRODUTOS;
    private static final String CHAVE_PARA_CURTIR = CurtidoEntity.PARA_CURTIR;
    private static final String CHAVE_PARA_DESCURTIR = CurtidoEntity.PARA_DESCURTIR;

    private final VenusDatabase banco;
    private final CurtidoDao dao;
    private final String uid;

    public CurtidosRepository(Context context) {
        this(context, DadosDaConta.uidAtual(context));
    }

    @VisibleForTesting
    public CurtidosRepository(Context context, @Nullable String uid) {
        this.banco = VenusDatabase.get(context);
        this.dao = banco.curtidoDao();
        this.uid = DadosDaConta.daConta(uid);
        trazerDoArquivoAntigo(context, uid);
    }

    public List<Long> getProdutoIds() {
        return ler(CHAVE_PRODUTOS);
    }

    public boolean contem(long produtoId) {
        return getProdutoIds().contains(produtoId);
    }

    // A tela grava e a sincronizacao marca o envio, cada uma na sua thread: a
    // transacao do banco faz o papel da trava que existia antes.

    public void curtir(long produtoId) {
        banco.runInTransaction(() -> {
            List<Long> produtos = ler(CHAVE_PRODUTOS);
            if (!produtos.contains(produtoId)) {
                produtos.add(0, produtoId);
            }
            List<Long> paraCurtir = ler(CHAVE_PARA_CURTIR);
            if (!paraCurtir.contains(produtoId)) {
                paraCurtir.add(produtoId);
            }
            List<Long> paraDescurtir = ler(CHAVE_PARA_DESCURTIR);
            paraDescurtir.remove(Long.valueOf(produtoId));
            gravar(produtos, paraCurtir, paraDescurtir);
        });
    }

    public void descurtir(long produtoId) {
        banco.runInTransaction(() -> {
            List<Long> produtos = ler(CHAVE_PRODUTOS);
            produtos.remove(Long.valueOf(produtoId));
            List<Long> paraCurtir = ler(CHAVE_PARA_CURTIR);
            paraCurtir.remove(Long.valueOf(produtoId));
            List<Long> paraDescurtir = ler(CHAVE_PARA_DESCURTIR);
            if (!paraDescurtir.contains(produtoId)) {
                paraDescurtir.add(produtoId);
            }
            gravar(produtos, paraCurtir, paraDescurtir);
        });
    }

    // ---- O que a SincronizacaoCurtidos usa ----

    List<Long> paraCurtirNaApi() {
        return ler(CHAVE_PARA_CURTIR);
    }

    List<Long> paraDescurtirNaApi() {
        return ler(CHAVE_PARA_DESCURTIR);
    }

    boolean temAlgoParaEnviar() {
        return !paraCurtirNaApi().isEmpty() || !paraDescurtirNaApi().isEmpty();
    }

    /** O POST desse produto foi aceito (ou ele ja estava curtido na API). */
    void marcarCurtidaEnviada(long produtoId) {
        tirarDaFila(CHAVE_PARA_CURTIR, produtoId);
    }

    /** O DELETE desse produto foi aceito (ou ele ja nao estava curtido na API). */
    void marcarDescurtidaEnviada(long produtoId) {
        tirarDaFila(CHAVE_PARA_DESCURTIR, produtoId);
    }

    /**
     * Troca os curtidos pelos da API: o servidor vence (o que foi curtido ou
     * descurtido pelo site aparece aqui), menos o que mudou no aparelho e
     * ainda esta na fila - esses continuam como a pessoa deixou.
     *
     * @param daApi na ordem da tela, o mais recente primeiro.
     * @return true se os curtidos mudaram.
     */
    boolean trocarPelosDaApi(List<Long> daApi) {
        return banco.runInTransaction(() -> {
            List<Long> atuais = ler(CHAVE_PRODUTOS);
            Set<Long> paraCurtir = new LinkedHashSet<>(ler(CHAVE_PARA_CURTIR));
            Set<Long> paraDescurtir = new LinkedHashSet<>(ler(CHAVE_PARA_DESCURTIR));

            // Os curtidos aqui que ainda nao subiram ficam no topo, na ordem da tela.
            Set<Long> novos = new LinkedHashSet<>();
            for (Long produtoId : atuais) {
                if (paraCurtir.contains(produtoId)) {
                    novos.add(produtoId);
                }
            }
            for (Long produtoId : daApi) {
                if (!paraDescurtir.contains(produtoId)) {
                    novos.add(produtoId);
                }
            }

            List<Long> resultado = new ArrayList<>(novos);
            if (resultado.equals(atuais)) {
                return false;
            }
            gravarLista(CHAVE_PRODUTOS, resultado);
            return true;
        });
    }

    private void tirarDaFila(String fila, long produtoId) {
        banco.runInTransaction(() -> {
            List<Long> ids = ler(fila);
            if (ids.remove(Long.valueOf(produtoId))) {
                gravarLista(fila, ids);
            }
        });
    }

    private List<Long> ler(String lista) {
        return new ArrayList<>(dao.ids(uid, lista));
    }

    // Chamado sempre de dentro de uma transacao.
    private void gravar(List<Long> produtos, List<Long> paraCurtir, List<Long> paraDescurtir) {
        gravarLista(CHAVE_PRODUTOS, produtos);
        gravarLista(CHAVE_PARA_CURTIR, paraCurtir);
        gravarLista(CHAVE_PARA_DESCURTIR, paraDescurtir);
    }

    private void gravarLista(String lista, List<Long> ids) {
        dao.apagarLista(uid, lista);
        List<CurtidoEntity> linhas = new ArrayList<>();
        for (int posicao = 0; posicao < ids.size(); posicao++) {
            linhas.add(new CurtidoEntity(uid, lista, ids.get(posicao), posicao));
        }
        dao.inserir(linhas);
    }

    /**
     * Traz para o banco os curtidos que estavam no arquivo de preferencias, de
     * antes do banco existir, e apaga o arquivo. Depois da primeira vez nao
     * acha mais nada.
     */
    private void trazerDoArquivoAntigo(Context context, @Nullable String uidDaConta) {
        SharedPreferences antigo = DadosDaConta.prefs(context, ARQUIVO, uidDaConta);
        if (antigo.getAll().isEmpty()) {
            return;
        }
        if (dao.quantos(uid) == 0) {
            List<Long> produtos = lerDoArquivo(antigo, CHAVE_PRODUTOS);
            List<Long> paraCurtir = lerDoArquivo(antigo, CHAVE_PARA_CURTIR);
            List<Long> paraDescurtir = lerDoArquivo(antigo, CHAVE_PARA_DESCURTIR);
            banco.runInTransaction(() -> gravar(produtos, paraCurtir, paraDescurtir));
        }
        antigo.edit().clear().commit();
        context.getApplicationContext()
                .deleteSharedPreferences(DadosDaConta.nomeDoArquivo(ARQUIVO, uidDaConta));
    }

    private static List<Long> lerDoArquivo(SharedPreferences antigo, String chave) {
        String salvo = antigo.getString(chave, null);
        if (salvo == null) {
            return new ArrayList<>();
        }
        Long[] ids;
        try {
            ids = new Gson().fromJson(salvo, Long[].class);
        } catch (JsonParseException e) {
            return new ArrayList<>();
        }
        if (ids == null) {
            return new ArrayList<>();
        }
        // Sem repetidos nem nulos: no banco o produto e chave dentro da lista.
        Set<Long> unicos = new LinkedHashSet<>(Arrays.asList(ids));
        unicos.remove(null);
        return new ArrayList<>(unicos);
    }
}
