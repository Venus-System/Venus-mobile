package com.venussystem.venusmobile.view.componente;

import android.content.Context;
import android.view.View;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.domain.avaliacao.RegrasDaAvaliacao;
import com.venussystem.venusmobile.model.Avaliacao;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class CartaoAvaliacao {

    public interface AoDenunciar {
        void aoDenunciar(Avaliacao avaliacao);
    }

    private static final DateTimeFormatter FORMATO_DATA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", new Locale("pt", "BR"));

    private CartaoAvaliacao() {
    }

    public static void preencher(View item, Avaliacao avaliacao, VotosDasAvaliacoes votos,
                                 AoDenunciar aoDenunciar) {
        Context contexto = item.getContext();
        item.setTag(avaliacao.getId());

        String autor = autor(contexto, avaliacao);
        ((TextView) item.findViewById(R.id.textAutor)).setText(autor);
        item.findViewById(R.id.textUsoVerificado)
                .setVisibility(avaliacao.isUsoVerificado() ? View.VISIBLE : View.GONE);

        TextView data = item.findViewById(R.id.textData);
        String quando = quando(contexto, avaliacao.getCriadaEm());
        data.setText(quando);
        data.setVisibility(quando == null ? View.GONE : View.VISIBLE);

        RatingBar estrelas = item.findViewById(R.id.estrelasAvaliacao);
        estrelas.setRating((float) avaliacao.getNota());
        estrelas.setContentDescription(contexto.getString(
                R.string.avaliacao_nota_descricao, nota(avaliacao.getNota())));

        ((TextView) item.findViewById(R.id.textTituloAvaliacao)).setText(avaliacao.getTitulo());
        TextView comentario = item.findViewById(R.id.textComentario);
        comentario.setText(avaliacao.getComentario());
        comentario.setVisibility(avaliacao.getComentario().isEmpty() ? View.GONE : View.VISIBLE);

        View rodape = item.findViewById(R.id.rodapeVotos);
        View blocoErro = item.findViewById(R.id.blocoErroVoto);
        if (avaliacao.isMinha()) {
            item.findViewById(R.id.textSua).setVisibility(View.VISIBLE);
            rodape.setVisibility(View.GONE);
            blocoErro.setVisibility(View.GONE);
            return;
        }
        item.findViewById(R.id.textSua).setVisibility(View.GONE);
        rodape.setVisibility(View.VISIBLE);

        VotosDasAvaliacoes.Estado estado = votos.de(avaliacao);
        boolean ocupado = estado.isOcupado();

        TextView sim = item.findViewById(R.id.btnVotoSim);
        sim.setText(contexto.getString(R.string.avaliacao_sim, estado.uteis(avaliacao)));
        prepararVoto(sim, estado.getMeu() == Avaliacao.Voto.UTIL, ocupado,
                v -> votos.escolher(avaliacao, Avaliacao.Voto.UTIL));

        TextView nao = item.findViewById(R.id.btnVotoNao);
        prepararVoto(nao, estado.getMeu() == Avaliacao.Voto.NAO_UTIL, ocupado,
                v -> votos.escolher(avaliacao, Avaliacao.Voto.NAO_UTIL));

        TextView denunciar = item.findViewById(R.id.btnDenunciar);
        denunciar.setContentDescription(
                contexto.getString(R.string.avaliacao_denunciar_descricao, autor));
        denunciar.setOnClickListener(v -> aoDenunciar.aoDenunciar(avaliacao));

        TextView textErro = item.findViewById(R.id.textErroVoto);
        View tentar = item.findViewById(R.id.btnTentarVoto);
        if (estado.isFalhouLeitura()) {
            textErro.setText(R.string.avaliacao_voto_nao_lido);
            tentar.setVisibility(View.VISIBLE);
            tentar.setOnClickListener(v -> votos.tentarLerDeNovo(avaliacao));
            blocoErro.setVisibility(View.VISIBLE);
        } else if (estado.getErro() != 0) {
            textErro.setText(estado.getErro());
            tentar.setVisibility(View.GONE);
            blocoErro.setVisibility(View.VISIBLE);
        } else {
            blocoErro.setVisibility(View.GONE);
        }
    }

    public static String nota(double valor) {
        return String.format(new Locale("pt", "BR"), "%.1f", valor);
    }

    private static void prepararVoto(TextView botao, boolean escolhido, boolean ocupado,
                                     View.OnClickListener aoTocar) {
        botao.setSelected(escolhido);
        botao.setEnabled(!ocupado);
        botao.setAlpha(ocupado ? 0.5f : 1f);
        botao.setOnClickListener(aoTocar);
    }

    private static String autor(Context contexto, Avaliacao avaliacao) {
        if (avaliacao.isMinha()) {
            return contexto.getString(R.string.avaliacao_autor_voce);
        }
        return avaliacao.getAutor() == null
                ? contexto.getString(R.string.avaliacao_autor_padrao)
                : avaliacao.getAutor();
    }

    @Nullable
    private static String quando(Context contexto, @Nullable OffsetDateTime criadaEm) {
        if (criadaEm == null) {
            return null;
        }
        LocalDate dia = criadaEm.atZoneSameInstant(ZoneId.systemDefault()).toLocalDate();
        long dias = RegrasDaAvaliacao.diasDesde(dia, LocalDate.now());
        if (dias < 1) {
            return contexto.getString(R.string.avaliacao_hoje);
        }
        if (dias == 1) {
            return contexto.getString(R.string.avaliacao_ontem);
        }
        if (dias < RegrasDaAvaliacao.DIAS_RELATIVOS) {
            return contexto.getResources()
                    .getQuantityString(R.plurals.avaliacao_ha_dias, (int) dias, (int) dias);
        }
        return FORMATO_DATA.format(dia);
    }
}
