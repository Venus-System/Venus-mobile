package com.venussystem.venusmobile.view.componente;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.Avaliacao;
import com.venussystem.venusmobile.repository.AvaliacoesRepository;

import java.util.HashMap;
import java.util.Map;

public class VotosDasAvaliacoes {

    public interface AoMudar {
        void aoMudar(long avaliacaoId);
    }

    public static final class Estado {
        @Nullable private Avaliacao.Voto meu;
        private boolean carregando;
        private boolean enviando;
        private boolean falhouLeitura;
        @StringRes private int erro;
        private long ajusteUteis;

        @Nullable
        public Avaliacao.Voto getMeu() {
            return meu;
        }

        public boolean isOcupado() {
            return carregando || enviando || falhouLeitura;
        }

        public boolean isEnviando() {
            return enviando;
        }

        public boolean isFalhouLeitura() {
            return falhouLeitura;
        }

        @StringRes
        public int getErro() {
            return erro;
        }

        public long uteis(Avaliacao avaliacao) {
            return Math.max(avaliacao.getVotosUteis() + ajusteUteis, 0);
        }
    }

    private final Map<Long, Estado> estados = new HashMap<>();
    private final AvaliacoesRepository repositorio;
    private final AoMudar aoMudar;

    public VotosDasAvaliacoes(AvaliacoesRepository repositorio, AoMudar aoMudar) {
        this.repositorio = repositorio;
        this.aoMudar = aoMudar;
    }

    public Estado de(Avaliacao avaliacao) {
        Estado estado = estados.get(avaliacao.getId());
        if (estado == null) {
            estado = new Estado();
            estados.put(avaliacao.getId(), estado);
            if (!avaliacao.isMinha()) {
                ler(avaliacao.getId(), estado);
            }
        }
        return estado;
    }

    public void tentarLerDeNovo(Avaliacao avaliacao) {
        Estado estado = de(avaliacao);
        if (estado.carregando) {
            return;
        }
        ler(avaliacao.getId(), estado);
        aoMudar.aoMudar(avaliacao.getId());
    }

    public void escolher(Avaliacao avaliacao, Avaliacao.Voto escolha) {
        Estado estado = de(avaliacao);
        if (estado.isOcupado()) {
            return;
        }
        Avaliacao.Voto anterior = estado.meu;
        Avaliacao.Voto novo = anterior == escolha ? null : escolha;
        estado.enviando = true;
        estado.erro = 0;
        aoMudar.aoMudar(avaliacao.getId());

        repositorio.votar(avaliacao.getId(), novo, anterior, resultado -> {
            estado.enviando = false;
            if (resultado == AvaliacoesRepository.Envio.OK) {
                estado.meu = novo;
                estado.ajusteUteis += (novo == Avaliacao.Voto.UTIL ? 1 : 0)
                        - (anterior == Avaliacao.Voto.UTIL ? 1 : 0);
            } else {
                estado.erro = resultado == AvaliacoesRepository.Envio.SEM_CONTA
                        ? R.string.avaliacao_sem_conta
                        : R.string.avaliacao_voto_erro;
            }
            aoMudar.aoMudar(avaliacao.getId());
        });
    }

    public void esquecer() {
        estados.clear();
    }

    private void ler(long avaliacaoId, Estado estado) {
        estado.carregando = true;
        estado.falhouLeitura = false;
        repositorio.meuVoto(avaliacaoId, (leu, voto) -> {
            estado.carregando = false;
            estado.falhouLeitura = !leu;
            if (leu) {
                estado.meu = voto;
            }
            aoMudar.aoMudar(avaliacaoId);
        });
    }
}
