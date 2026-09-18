package com.venussystem.venusmobile.repository;

import androidx.lifecycle.LiveData;

import com.venussystem.venusmobile.model.ItemHistorico;
import com.venussystem.venusmobile.model.Produto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Historico dos produtos escaneados.
 *
 * O scan ainda nao existe no app, entao nao ha historico de verdade para
 * mostrar; por enquanto a tela fica no estado vazio (MOSTRAR_EXEMPLO = false).
 *
 * Quando o scan entrar, cada leitura vira uma entrada aqui. A API ja tem onde
 * registrar isso - /api/scan-sessions e /api/analysis-results/user/{userId}
 * (combinados via .../analysis-result/{id}/full ja trazem usuario, produto e
 * data) - falta so o app disparar o scan e consumir essas rotas.
 */
public class HistoricoRepository {

    private static final boolean MOSTRAR_EXEMPLO = false;

    // Quantos produtos aparecem em cada grupo e ha quantos dias esse grupo
    // aconteceu. As duas listas andam juntas, uma posicao para cada grupo.
    private static final int[] PRODUTOS_POR_GRUPO = {2, 2, 1, 3, 2, 3};
    private static final int[] DIAS_ATRAS = {0, 7, 12, 17, 25, 38};

    private final ProdutoRepository produtos = new ProdutoRepository();

    public LiveData<List<Produto>> getCatalogo() {
        return produtos.getCatalogo();
    }

    public void carregar() {
        produtos.carregar(false);
    }

    public List<ItemHistorico> montar(List<Produto> catalogo) {
        List<ItemHistorico> entradas = new ArrayList<>();
        if (!MOSTRAR_EXEMPLO || catalogo == null || catalogo.isEmpty()) {
            return entradas;
        }

        // O catalogo vem agrupado por marca, entao pegar os primeiros encheria
        // a tela so de uma marca. Andamos de passo em passo para variar.
        int total = 0;
        for (int quantidade : PRODUTOS_POR_GRUPO) {
            total += quantidade;
        }
        int passo = Math.max(1, catalogo.size() / total);

        LocalDate hoje = LocalDate.now();
        int proximo = 0;

        for (int grupo = 0; grupo < PRODUTOS_POR_GRUPO.length; grupo++) {
            LocalDate data = hoje.minusDays(DIAS_ATRAS[grupo]);

            for (int i = 0; i < PRODUTOS_POR_GRUPO[grupo]; i++) {
                if (proximo >= catalogo.size()) {
                    return entradas;
                }
                entradas.add(new ItemHistorico(catalogo.get(proximo), data));
                proximo += passo;
            }
        }
        return entradas;
    }
}
