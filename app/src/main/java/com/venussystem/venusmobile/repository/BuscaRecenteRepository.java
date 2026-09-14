package com.venussystem.venusmobile.repository;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Guarda os termos que o usuario ja buscou, no proprio aparelho.
 *
 * A API nao tem nada de historico de busca - nenhuma das rotas registra ou
 * devolve termo pesquisado - entao isso e estado local mesmo, e some junto com
 * os dados do app.
 *
 * Mesma forma do PerfilRepository: lista ordenada num unico texto separado por
 * caractere de controle, porque a ordem (mais recente primeiro) importa.
 */
public class BuscaRecenteRepository {

    private static final String ARQUIVO = "venus_busca";
    private static final String CHAVE_RECENTES = "buscas_recentes";

    // Caractere de controle "unit separator" (31). Nao aparece em termo
    // digitado, entao nunca colide com o conteudo.
    private static final String SEPARADOR = String.valueOf((char) 31);

    private static final int LIMITE = 8;
    private static final int MINIMO_CARACTERES = 2;

    private final SharedPreferences prefs;

    public BuscaRecenteRepository(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE);
    }

    public List<String> getRecentes() {
        String salvo = prefs.getString(CHAVE_RECENTES, "");
        if (salvo == null || salvo.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(salvo.split(SEPARADOR)));
    }

    /**
     * Coloca o termo no topo da lista. Busca repetida nao vira entrada nova:
     * a anterior sai e a mesma volta como a mais recente.
     */
    public void registrar(String termo) {
        if (termo == null) {
            return;
        }
        String limpo = termo.trim();
        if (limpo.length() < MINIMO_CARACTERES) {
            return;
        }

        List<String> atuais = getRecentes();
        removerIgnorandoCaixa(atuais, limpo);
        atuais.add(0, limpo);

        while (atuais.size() > LIMITE) {
            atuais.remove(atuais.size() - 1);
        }
        salvar(atuais);
    }

    public void remover(String termo) {
        List<String> atuais = getRecentes();
        removerIgnorandoCaixa(atuais, termo);
        salvar(atuais);
    }

    private void removerIgnorandoCaixa(List<String> lista, String termo) {
        if (termo == null) {
            return;
        }
        String alvo = termo.trim().toLowerCase(Locale.getDefault());
        lista.removeIf(item -> item.toLowerCase(Locale.getDefault()).equals(alvo));
    }

    private void salvar(List<String> termos) {
        prefs.edit().putString(CHAVE_RECENTES, String.join(SEPARADOR, termos)).apply();
    }
}
