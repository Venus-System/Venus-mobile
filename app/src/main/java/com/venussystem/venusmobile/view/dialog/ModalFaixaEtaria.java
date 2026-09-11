package com.venussystem.venusmobile.view.dialog;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.venussystem.venusmobile.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Modal de faixa etaria: e a unica pergunta do perfil com muitas opcoes (7),
 * entao usa um dropdown em vez de chips - os chips das outras perguntas
 * ficariam apertados demais com tantas opcoes num card pequeno.
 */
public final class ModalFaixaEtaria {

    private static final String[] TAGS = {
            "UNDER_13", "AGE_13_17", "AGE_18_24", "AGE_25_34",
            "AGE_35_44", "AGE_45_54", "AGE_55_PLUS",
    };

    private static final int[] ROTULOS = {
            R.string.faixa_menos_13, R.string.faixa_13_17, R.string.faixa_18_24,
            R.string.faixa_25_34, R.string.faixa_35_44, R.string.faixa_45_54,
            R.string.faixa_acima_55,
    };

    public interface AoSalvar {
        void aoConfirmar(String tag, String rotulo);
    }

    private ModalFaixaEtaria() {
    }

    public static void mostrar(Context contexto, String tagAtual, AoSalvar aoSalvar) {
        View conteudo = LayoutInflater.from(contexto)
                .inflate(R.layout.dialog_escolha_faixa_etaria, null);

        AlertDialog dialog = new AlertDialog.Builder(contexto)
                .setView(conteudo)
                .create();

        List<String> rotulos = new ArrayList<>();
        rotulos.add(contexto.getString(R.string.faixa_selecione));
        for (int rotulo : ROTULOS) {
            rotulos.add(contexto.getString(rotulo));
        }

        Spinner spinner = conteudo.findViewById(R.id.spinnerFaixaEtaria);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                contexto, android.R.layout.simple_spinner_item, rotulos);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        int posicaoAtual = 0;
        String rotuloAtual = null;
        for (int i = 0; i < TAGS.length; i++) {
            if (TAGS[i].equals(tagAtual)) {
                posicaoAtual = i + 1;
                rotuloAtual = contexto.getString(ROTULOS[i]);
                break;
            }
        }
        spinner.setSelection(posicaoAtual);

        boolean respondido = rotuloAtual != null;
        ((TextView) conteudo.findViewById(R.id.textAtual)).setText(contexto.getString(
                R.string.modal_atual,
                respondido ? rotuloAtual : contexto.getString(R.string.perfil_pendente)));

        conteudo.findViewById(R.id.btnCancelarModal).setOnClickListener(v -> dialog.dismiss());
        conteudo.findViewById(R.id.btnSalvarModal).setOnClickListener(v -> {
            int posicao = spinner.getSelectedItemPosition();
            if (posicao == 0) {
                Toast.makeText(contexto, R.string.pergunta_escolha_uma, Toast.LENGTH_SHORT).show();
                return;
            }
            int indice = posicao - 1;
            aoSalvar.aoConfirmar(TAGS[indice], contexto.getString(ROTULOS[indice]));
            dialog.dismiss();
        });

        ModalEscolhaUnica.prepararJanela(dialog, contexto);
        dialog.show();
    }
}
