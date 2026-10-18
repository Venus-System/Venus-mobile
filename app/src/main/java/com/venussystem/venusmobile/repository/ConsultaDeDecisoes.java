package com.venussystem.venusmobile.repository;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.venussystem.venusmobile.domain.aviso.AvisoDeAnalise;
import com.venussystem.venusmobile.model.ProdutoEmAnalise;
import com.venussystem.venusmobile.model.ProdutoEmAnalise.Situacao;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Descobre quais dos produtos enviados pela pessoa foram aprovados ou recusados
 * desde a ultima conferencia, para o app avisar. Sem servidor mandando nada: o
 * app mesmo pergunta a API (a mesma lista da tela Produtos em analise) e compara
 * com o que ja viu.
 *
 * Guarda por conta so os ids dos produtos ja decididos que ela ja viu. A
 * primeira conferencia de uma conta so aprende o que ja estava decidido - senao
 * quem acabou de instalar o app receberia um aviso para cada produto antigo.
 * Dai em diante, um produto decidido que ainda nao estava na lista e novo: foi
 * enviado depois e acabou de ser decidido.
 */
public class ConsultaDeDecisoes {

    static final String ARQUIVO = "venus_decisoes";
    private static final String CHAVE_INICIADO = "iniciado";
    private static final String CHAVE_DECIDIDOS = "decididos";

    /** De onde vem a lista; a versao real le a API ({@link ProdutosEmAnaliseRepository}). */
    public interface Fonte {
        /** @return a lista, do mais recente para o mais antigo; null se nao deu para buscar. */
        @Nullable
        List<ProdutoEmAnalise> buscar();
    }

    public static final class Resultado {
        /** Um aviso para cada produto decidido desde a ultima conferencia. */
        public final List<AvisoDeAnalise> avisos;
        /** Ainda ha produto esperando decisao: vale continuar conferindo. */
        public final boolean aindaAguardando;
        /** Nao deu para buscar (sem rede, API dormindo): tentar de novo mais tarde. */
        public final boolean falhou;

        Resultado(List<AvisoDeAnalise> avisos, boolean aindaAguardando, boolean falhou) {
            this.avisos = avisos;
            this.aindaAguardando = aindaAguardando;
            this.falhou = falhou;
        }
    }

    private final Fonte fonte;
    private final SharedPreferences prefs;

    public ConsultaDeDecisoes(Context context, @Nullable String uid) {
        this(() -> new ProdutosEmAnaliseRepository(context).buscarAgora(),
                DadosDaConta.prefs(context, ARQUIVO, uid));
    }

    @VisibleForTesting
    ConsultaDeDecisoes(Fonte fonte, SharedPreferences prefs) {
        this.fonte = fonte;
        this.prefs = prefs;
    }

    public Resultado verificar() {
        List<ProdutoEmAnalise> produtos = fonte.buscar();
        if (produtos == null) {
            return new Resultado(new ArrayList<>(), true, true);
        }

        boolean primeiraVez = !prefs.getBoolean(CHAVE_INICIADO, false);
        Set<String> jaVistos = prefs.getStringSet(CHAVE_DECIDIDOS, new HashSet<>());
        // So os ids da lista de agora: um produto fora da primeira pagina nao volta
        // a aparecer, e assim o arquivo nunca cresce.
        Set<String> decididosAgora = new HashSet<>();
        List<AvisoDeAnalise> avisos = new ArrayList<>();
        boolean aindaAguardando = false;

        for (ProdutoEmAnalise produto : produtos) {
            if (produto.getSituacao() == Situacao.AGUARDANDO || produto.getSituacao() == Situacao.EM_ANALISE) {
                aindaAguardando = true;
                continue;
            }
            decididosAgora.add(produto.getId());
            if (!primeiraVez && !jaVistos.contains(produto.getId())) {
                AvisoDeAnalise aviso = AvisoDeAnalise.de(produto);
                if (aviso != null) {
                    avisos.add(aviso);
                }
            }
        }

        prefs.edit()
                .putBoolean(CHAVE_INICIADO, true)
                .putStringSet(CHAVE_DECIDIDOS, decididosAgora)
                .apply();
        return new Resultado(avisos, aindaAguardando, false);
    }
}
