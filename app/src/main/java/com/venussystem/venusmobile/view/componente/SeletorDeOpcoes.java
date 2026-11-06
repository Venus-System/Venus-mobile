package com.venussystem.venusmobile.view.componente;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.StringRes;
import androidx.appcompat.widget.ListPopupWindow;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.repository.PerfilRepository;

import java.util.ArrayList;
import java.util.List;

public class SeletorDeOpcoes {

    public interface AoEscolher {
        void aoEscolher(String valor);
    }

    private final Context context;
    private final View blocoOutro;
    private final EditText campoOutro;
    private final AoEscolher aoEscolher;
    private final List<String> itens = new ArrayList<>();
    private final ArrayAdapter<String> adapter;
    private final ListPopupWindow popup;

    public SeletorDeOpcoes(TextView campo, View blocoOutro, @StringRes int hintOutro,
                           List<String> opcoes, AoEscolher aoEscolher) {
        this.context = campo.getContext();
        this.blocoOutro = blocoOutro;
        this.campoOutro = blocoOutro.findViewById(R.id.campoOutro);
        this.aoEscolher = aoEscolher;

        adapter = new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, itens);
        trocarOpcoes(opcoes);

        popup = new ListPopupWindow(context);
        popup.setAnchorView(campo);
        popup.setAdapter(adapter);
        popup.setModal(true);
        popup.setInputMethodMode(ListPopupWindow.INPUT_METHOD_NOT_NEEDED);
        popup.setDropDownGravity(Gravity.START);
        int larguraDisponivel = context.getResources().getDisplayMetrics().widthPixels
                - 2 * context.getResources().getDimensionPixelSize(R.dimen.espaco_medio);
        int larguraMaxima = context.getResources().getDimensionPixelSize(
                R.dimen.largura_max_dialogo);
        popup.setWidth(Math.min(larguraDisponivel, larguraMaxima));
        popup.setVerticalOffset(0);
        popup.setOnItemClickListener((pai, v, posicao, id) -> {
            popup.dismiss();
            escolher(posicao);
        });

        campo.setOnClickListener(v -> abrir());

        campoOutro.setHint(hintOutro);
        campoOutro.setOnEditorActionListener((v, acao, evento) -> {
            if (acao == EditorInfo.IME_ACTION_DONE) {
                adicionarOutro();
                return true;
            }
            return false;
        });
        blocoOutro.findViewById(R.id.btnAdicionarOutro).setOnClickListener(v -> adicionarOutro());
    }

    public void trocarOpcoes(List<String> opcoes) {
        itens.clear();
        itens.addAll(opcoesComExtras(context, opcoes));
        adapter.notifyDataSetChanged();
    }

    public static List<String> opcoesComExtras(Context context, List<String> opcoes) {
        List<String> completas = new ArrayList<>();
        completas.add(context.getString(R.string.opcao_nenhuma));
        if (opcoes != null) {
            completas.addAll(opcoes);
        }
        completas.add(context.getString(R.string.opcao_outro));
        return completas;
    }

    private void abrir() {
        esconderTeclado();
        popup.show();
    }

    private void escolher(int posicao) {
        if (posicao == 0) {
            blocoOutro.setVisibility(View.GONE);
            aoEscolher.aoEscolher(PerfilRepository.NENHUMA);
        } else if (posicao == itens.size() - 1) {
            mostrarOutro();
        } else {
            aoEscolher.aoEscolher(itens.get(posicao));
        }
    }

    private void mostrarOutro() {
        blocoOutro.setVisibility(View.VISIBLE);
        campoOutro.requestFocus();
        InputMethodManager teclado = context.getSystemService(InputMethodManager.class);
        if (teclado != null) {
            teclado.showSoftInput(campoOutro, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void adicionarOutro() {
        String texto = campoOutro.getText().toString().trim();
        if (texto.isEmpty()) {
            return;
        }
        campoOutro.setText("");
        esconderTeclado();
        blocoOutro.setVisibility(View.GONE);
        aoEscolher.aoEscolher(texto);
    }

    private void esconderTeclado() {
        InputMethodManager teclado = context.getSystemService(InputMethodManager.class);
        if (teclado != null) {
            teclado.hideSoftInputFromWindow(campoOutro.getWindowToken(), 0);
        }
    }

    public static List<String> aplicar(List<String> atuais, String novo) {
        List<String> resultado = new ArrayList<>();
        boolean nenhuma = PerfilRepository.NENHUMA.equals(novo);
        if (!nenhuma) {
            for (String atual : atuais) {
                if (!PerfilRepository.NENHUMA.equals(atual)) {
                    resultado.add(atual);
                }
            }
        }
        for (String atual : resultado) {
            if (atual.equalsIgnoreCase(novo)) {
                return resultado;
            }
        }
        resultado.add(novo);
        return resultado;
    }

    public static String rotulo(Context context, String valor) {
        return PerfilRepository.NENHUMA.equals(valor)
                ? context.getString(R.string.opcao_nenhuma)
                : valor;
    }
}
