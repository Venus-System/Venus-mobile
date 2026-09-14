package com.venussystem.venusmobile.view.dialog;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.venussystem.venusmobile.R;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Modal compacto de escolha unica, usado para editar uma resposta do perfil
 * sem sair da tela (ao contrario das telas cheias do questionario inicial).
 *
 * Reaproveitado pelas 5 perguntas que sao de escolha simples entre poucas
 * opcoes (pele, fototipo, sensibilidade, cabelo, couro cabeludo); a faixa
 * etaria usa o ModalFaixaEtaria porque a interacao la e um dropdown, nao chips.
 */
public final class ModalEscolhaUnica {

    public interface AoSalvar {
        void aoConfirmar(String tag, String rotulo);
    }

    public static class Opcao {
        final String tag;
        @StringRes final int rotulo;

        public Opcao(String tag, @StringRes int rotulo) {
            this.tag = tag;
            this.rotulo = rotulo;
        }
    }

    private ModalEscolhaUnica() {
    }

    public static void mostrar(Context contexto, @StringRes int pergunta,
                               @Nullable String tagAtual, List<Opcao> opcoes, AoSalvar aoSalvar) {
        View conteudo = LayoutInflater.from(contexto)
                .inflate(R.layout.dialog_escolha_unica, null);

        ((TextView) conteudo.findViewById(R.id.textPergunta)).setText(pergunta);

        AlertDialog dialog = new AlertDialog.Builder(contexto)
                .setView(conteudo)
                .create();

        ChipGroup grupo = conteudo.findViewById(R.id.grupoOpcoes);
        Map<Integer, Opcao> opcaoPorIdDoChip = new LinkedHashMap<>();
        TextView textAtual = conteudo.findViewById(R.id.textAtual);
        String rotuloAtual = null;

        for (Opcao opcao : opcoes) {
            Chip chip = new Chip(contexto);
            chip.setId(View.generateViewId());
            chip.setText(opcao.rotulo);
            chip.setCheckable(true);
            chip.setChipBackgroundColorResource(R.color.chip_fundo_escolha);
            chip.setChipStrokeColorResource(R.color.chip_borda_escolha);
            chip.setChipStrokeWidth(1.2f * contexto.getResources().getDisplayMetrics().density);
            chip.setTextColor(contexto.getResources().getColorStateList(
                    R.color.chip_texto_escolha, contexto.getTheme()));
            chip.setCheckedIconVisible(false);
            chip.setChipCornerRadius(18f * contexto.getResources().getDisplayMetrics().density);

            grupo.addView(chip);
            opcaoPorIdDoChip.put(chip.getId(), opcao);

            if (opcao.tag.equals(tagAtual)) {
                chip.setChecked(true);
                rotuloAtual = contexto.getString(opcao.rotulo);
            }
        }

        boolean respondido = rotuloAtual != null;
        textAtual.setText(contexto.getString(R.string.modal_atual,
                respondido ? rotuloAtual : contexto.getString(R.string.perfil_pendente)));

        conteudo.findViewById(R.id.btnCancelarModal).setOnClickListener(v -> dialog.dismiss());
        conteudo.findViewById(R.id.btnSalvarModal).setOnClickListener(v -> {
            int idSelecionado = grupo.getCheckedChipId();
            if (idSelecionado == View.NO_ID) {
                Toast.makeText(contexto, R.string.pergunta_escolha_uma, Toast.LENGTH_SHORT).show();
                return;
            }
            Opcao escolhida = opcaoPorIdDoChip.get(idSelecionado);
            aoSalvar.aoConfirmar(escolhida.tag, contexto.getString(escolhida.rotulo));
            dialog.dismiss();
        });

        prepararJanela(dialog, contexto);
        dialog.show();
    }

    /**
     * O fundo padrao da janela de dialog e um retangulo branco que esconderia
     * os cantos arredondados do nosso layout - por isso fica transparente, e
     * quem desenha o card e o proprio bg_modal_escolha do conteudo.
     */
    static void prepararJanela(@NonNull AlertDialog dialog, Context contexto) {
        if (dialog.getWindow() == null) {
            return;
        }
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        int largura = (int) (contexto.getResources().getDisplayMetrics().widthPixels * 0.88f);
        dialog.getWindow().setLayout(largura, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
