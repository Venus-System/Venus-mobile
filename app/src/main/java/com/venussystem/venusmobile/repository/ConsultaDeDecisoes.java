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

public class ConsultaDeDecisoes {

    static final String ARQUIVO = "venus_decisoes";
    private static final String CHAVE_INICIADO = "iniciado";
    private static final String CHAVE_DECIDIDOS = "decididos";

    public interface Fonte {
        @Nullable
        List<ProdutoEmAnalise> buscar();
    }

    public static final class Resultado {
        public final List<AvisoDeAnalise> avisos;
        public final boolean aindaAguardando;
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
