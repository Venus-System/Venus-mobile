package com.venussystem.venusmobile.repository;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Guarda as respostas do questionario no proprio aparelho.
 *
 * Cada resposta e salva em duas pecas: a TAG, que e o valor que a API entende
 * (COMBINATION, MEDIUM, 2A...), e o ROTULO, que e o texto que o usuario leu na
 * tela ("Mista", "Media"). Guardar os dois evita ter que manter uma tabela de
 * traducao so para escrever a resposta de volta no perfil.
 *
 * Quando existir endpoint de perfil na API, isto vira o cache local dela.
 */
public class PerfilRepository {

    private static final String ARQUIVO = "venus_perfil";
    private static final String CHAVE_RESPONDEU = "respondeu_questionario";

    private static final String SUFIXO_TAG = "_tag";
    private static final String SUFIXO_ROTULO = "_rotulo";

    // Caractere de controle "unit separator" (31). Nao aparece em nome de
    // alergia nem de preferencia, entao nunca colide com o que o usuario digita.
    private static final String SEPARADOR = String.valueOf((char) 31);

    public static final String TIPO_CABELO = "tipo_cabelo";
    public static final String COURO_CABELUDO = "couro_cabeludo";
    public static final String TIPO_PELE = "tipo_pele";
    public static final String SENSIBILIDADE = "sensibilidade";
    public static final String FOTOTIPO = "fototipo";
    public static final String FAIXA_ETARIA = "faixa_etaria";
    public static final String ALERGIAS = "alergias";
    public static final String PREFERENCIAS = "preferencias";

    private final SharedPreferences prefs;

    public PerfilRepository(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE);
    }

    public boolean jaRespondeuQuestionario() {
        return prefs.getBoolean(CHAVE_RESPONDEU, false);
    }

    public void marcarQuestionarioRespondido() {
        prefs.edit().putBoolean(CHAVE_RESPONDEU, true).apply();
    }

    public void salvarResposta(String chave, String tag, String rotulo) {
        prefs.edit()
                .putString(chave + SUFIXO_TAG, tag)
                .putString(chave + SUFIXO_ROTULO, rotulo)
                .apply();
    }

    public String getTag(String chave) {
        return prefs.getString(chave + SUFIXO_TAG, null);
    }

    public String getRotulo(String chave) {
        return prefs.getString(chave + SUFIXO_ROTULO, null);
    }

    /**
     * Listas viram um texto unico separado pelo caractere de controle, em vez de
     * um StringSet: o Set do SharedPreferences nao garante ordem, e aqui a ordem
     * em que o usuario escolheu importa para os chips voltarem iguais.
     */
    public void salvarLista(String chave, List<String> itens) {
        prefs.edit().putString(chave, String.join(SEPARADOR, itens)).apply();
    }

    public List<String> getLista(String chave) {
        String salvo = prefs.getString(chave, "");
        if (salvo == null || salvo.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(salvo.split(SEPARADOR)));
    }

    public void limpar() {
        prefs.edit().clear().apply();
    }
}
