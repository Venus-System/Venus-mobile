package com.venussystem.venusmobile.repository;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.venussystem.venusmobile.domain.perfil.ProgressoPerfil;
import com.venussystem.venusmobile.repository.api.MapeadorPerfilApi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PerfilRepository implements MapeadorPerfilApi.Respostas {

    static final String ARQUIVO = "venus_perfil";
    private static final String CHAVE_RESPONDEU = "respondeu_questionario";

    private static final String CHAVE_VERSAO = "versao_perfil";
    private static final String CHAVE_VERSAO_ENVIADA = "versao_enviada";
    private static final String CHAVE_ALTERADO_EM = "perfil_alterado_em";

    private static final long VERSAO_INICIAL = 1L;

    private static final String SUFIXO_TAG = "_tag";
    private static final String SUFIXO_ROTULO = "_rotulo";

    private static final String SEPARADOR = String.valueOf((char) 31);

    public static final String GENERO = "genero";
    public static final String TIPO_CABELO = "tipo_cabelo";
    public static final String COURO_CABELUDO = "couro_cabeludo";
    public static final String TIPO_PELE = "tipo_pele";
    public static final String SENSIBILIDADE = "sensibilidade";
    public static final String CONDICOES_PELE = "condicoes_pele";
    public static final String FOTOTIPO = "fototipo";
    public static final String FAIXA_ETARIA = "faixa_etaria";
    public static final String GESTACAO = "gestacao";
    public static final String ALERGIAS = "alergias";
    public static final String PREFERENCIAS = "preferencias";

    public static final String ALERGIAS_NA_API = "alergias_na_api";
    public static final String ETIQUETAS_NA_API = "etiquetas_na_api";

    public static final String NENHUMA = "NENHUMA";
    public static final String PREFIRO_NAO_DIZER = "PREFIRO_NAO_DIZER";

    private final SharedPreferences prefs;

    public PerfilRepository(Context context) {
        this(context, DadosDaConta.uidAtual(context));
    }

    @VisibleForTesting
    public PerfilRepository(Context context, @Nullable String uid) {
        this.prefs = DadosDaConta.prefs(context, ARQUIVO, uid);
    }

    public boolean jaRespondeuQuestionario() {
        return prefs.getBoolean(CHAVE_RESPONDEU, false);
    }

    private static final List<String> CAMPOS_DO_PROGRESSO = Arrays.asList(
            FAIXA_ETARIA, GENERO, TIPO_PELE, FOTOTIPO, SENSIBILIDADE, TIPO_CABELO,
            COURO_CABELUDO, GESTACAO, CONDICOES_PELE);
    private static final Set<String> CAMPOS_DE_LISTA =
            new HashSet<>(Arrays.asList(GESTACAO, CONDICOES_PELE));

    public ProgressoPerfil progresso() {
        return ProgressoPerfil.calcular(jaRespondeuQuestionario(), CAMPOS_DO_PROGRESSO,
                chave -> CAMPOS_DE_LISTA.contains(chave)
                        ? !getLista(chave).isEmpty()
                        : getTag(chave) != null);
    }

    public void marcarQuestionarioRespondido() {
        prefs.edit().putBoolean(CHAVE_RESPONDEU, true).apply();
    }

    public void salvarResposta(String chave, String tag, String rotulo) {
        prefs.edit()
                .putString(chave + SUFIXO_TAG, tag)
                .putString(chave + SUFIXO_ROTULO, rotulo)
                .putLong(CHAVE_VERSAO, versaoAtual() + 1)
                .putLong(CHAVE_ALTERADO_EM, System.currentTimeMillis())
                .apply();
    }

    @Override
    public String getTag(String chave) {
        return prefs.getString(chave + SUFIXO_TAG, null);
    }

    public String getRotulo(String chave) {
        return prefs.getString(chave + SUFIXO_ROTULO, null);
    }

    public void salvarLista(String chave, List<String> itens) {
        prefs.edit()
                .putString(chave, String.join(SEPARADOR, itens))
                .putLong(CHAVE_VERSAO, versaoAtual() + 1)
                .putLong(CHAVE_ALTERADO_EM, System.currentTimeMillis())
                .apply();
    }

    @Override
    public List<String> getLista(String chave) {
        String salvo = prefs.getString(chave, "");
        if (salvo == null || salvo.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(salvo.split(SEPARADOR)));
    }

    public long versaoAtual() {
        return prefs.getLong(CHAVE_VERSAO, VERSAO_INICIAL);
    }

    public long alteradoEm() {
        return prefs.getLong(CHAVE_ALTERADO_EM, 0L);
    }

    public boolean temAlteracaoParaEnviar() {
        return versaoAtual() != prefs.getLong(CHAVE_VERSAO_ENVIADA, 0L);
    }

    public void marcarEnviada(long versao) {
        prefs.edit().putLong(CHAVE_VERSAO_ENVIADA, versao).apply();
    }

    public Set<Long> getIdsNaApi(String chave) {
        Set<Long> ids = new HashSet<>();
        for (String id : getLista(chave)) {
            try {
                ids.add(Long.parseLong(id));
            } catch (NumberFormatException e) {
            }
        }
        return ids;
    }

    public void salvarIdsNaApi(String chave, Set<Long> ids) {
        List<String> textos = new ArrayList<>();
        for (Long id : ids) {
            textos.add(String.valueOf(id));
        }
        prefs.edit().putString(chave, String.join(SEPARADOR, textos)).apply();
    }

    public void limpar() {
        String alergias = prefs.getString(ALERGIAS_NA_API, null);
        String etiquetas = prefs.getString(ETIQUETAS_NA_API, null);

        SharedPreferences.Editor editor = prefs.edit().clear();
        if (alergias != null) {
            editor.putString(ALERGIAS_NA_API, alergias);
        }
        if (etiquetas != null) {
            editor.putString(ETIQUETAS_NA_API, etiquetas);
        }
        editor.apply();
    }
}
