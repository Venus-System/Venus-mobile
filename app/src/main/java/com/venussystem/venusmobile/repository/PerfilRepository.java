package com.venussystem.venusmobile.repository;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.venussystem.venusmobile.repository.api.MapeadorPerfilApi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Guarda as respostas do questionario no proprio aparelho.
 *
 * Cada resposta e salva em duas pecas: a TAG, que e o valor que a API entende
 * (COMBINATION, MEDIUM, 2A...), e o ROTULO, que e o texto que o usuario leu na
 * tela ("Mista", "Media"). Guardar os dois evita ter que manter uma tabela de
 * traducao so para escrever a resposta de volta no perfil.
 *
 * O aparelho continua sendo a fonte do que a tela mostra; a copia na API
 * (perfil, preferencias, etiquetas e alergias) e mantida pelo
 * SincronizacaoRepository, que usa a versao abaixo para saber o que falta enviar.
 *
 * Cada conta tem o seu questionario (ver DadosDaConta): sem isso, a resposta
 * de uma conta iria para o perfil de outra na API.
 */
public class PerfilRepository implements MapeadorPerfilApi.Respostas {

    static final String ARQUIVO = "venus_perfil";
    private static final String CHAVE_RESPONDEU = "respondeu_questionario";

    // Um contador em vez de um "precisa enviar" true/false: se a pessoa editar
    // enquanto um envio esta no meio do caminho, a versao sobe de novo e o envio
    // antigo nao consegue marcar como enviada uma resposta que ele nem leu.
    private static final String CHAVE_VERSAO = "versao_perfil";
    private static final String CHAVE_VERSAO_ENVIADA = "versao_enviada";

    // Comeca em 1 (e nao em 0, o padrao da enviada) para que quem ja tinha
    // respondido antes desta versao do app tambem tenha as respostas enviadas.
    private static final long VERSAO_INICIAL = 1L;

    private static final String SUFIXO_TAG = "_tag";
    private static final String SUFIXO_ROTULO = "_rotulo";

    // Caractere de controle "unit separator" (31). Nao aparece em nome de
    // alergia nem de preferencia, entao nunca colide com o que o usuario digita.
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

    // O que o app ja gravou na API, por id do catalogo (ver getIdsNaApi).
    public static final String ALERGIAS_NA_API = "alergias_na_api";
    public static final String ETIQUETAS_NA_API = "etiquetas_na_api";

    // Respostas de "nenhuma" e "prefiro nao dizer" sao guardadas como item da
    // lista, e nao como lista vazia: sem isso nao daria para diferenciar quem
    // respondeu que nao tem nada de quem ainda nao passou pela tela.
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

    public void marcarQuestionarioRespondido() {
        prefs.edit().putBoolean(CHAVE_RESPONDEU, true).apply();
    }

    public void salvarResposta(String chave, String tag, String rotulo) {
        prefs.edit()
                .putString(chave + SUFIXO_TAG, tag)
                .putString(chave + SUFIXO_ROTULO, rotulo)
                .putLong(CHAVE_VERSAO, versaoAtual() + 1)
                .apply();
    }

    @Override
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
        prefs.edit()
                .putString(chave, String.join(SEPARADOR, itens))
                .putLong(CHAVE_VERSAO, versaoAtual() + 1)
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

    public boolean temAlteracaoParaEnviar() {
        return versaoAtual() != prefs.getLong(CHAVE_VERSAO_ENVIADA, 0L);
    }

    /**
     * Registra que as respostas ate essa versao ja estao na API. Quem chama
     * passa a versao lida ANTES de montar o envio, nunca a atual do momento.
     */
    public void marcarEnviada(long versao) {
        prefs.edit().putLong(CHAVE_VERSAO_ENVIADA, versao).apply();
    }

    /**
     * Ids do catalogo que o proprio app gravou na API (alergias ou etiquetas).
     * E so controle do envio: nao muda a versao, porque nao e resposta nova.
     */
    public Set<Long> getIdsNaApi(String chave) {
        Set<Long> ids = new HashSet<>();
        for (String id : getLista(chave)) {
            try {
                ids.add(Long.parseLong(id));
            } catch (NumberFormatException e) {
                // Valor corrompido: melhor esquecer do que travar o envio.
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
        prefs.edit().clear().apply();
    }
}
