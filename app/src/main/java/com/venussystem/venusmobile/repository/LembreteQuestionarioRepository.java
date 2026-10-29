package com.venussystem.venusmobile.repository;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.venussystem.venusmobile.domain.lembrete.AgendaQuestionario;

import java.time.ZoneId;

public class LembreteQuestionarioRepository {

    static final String ARQUIVO = "venus_lembrete_questionario";
    private static final String CHAVE_ATIVO = "ativo";
    private static final String CHAVE_ANCORA = "ancora";
    private static final String CHAVE_ENVIADOS = "enviados";

    private final SharedPreferences prefs;

    public LembreteQuestionarioRepository(Context context) {
        this(context, DadosDaConta.uidAtual(context));
    }

    public LembreteQuestionarioRepository(Context context, @Nullable String uid) {
        this(DadosDaConta.prefs(context, ARQUIVO, uid));
    }

    @VisibleForTesting
    LembreteQuestionarioRepository(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    public boolean ativo() {
        return prefs.getBoolean(CHAVE_ATIVO, false);
    }

    public void ativar(long agoraMs) {
        prefs.edit()
                .putBoolean(CHAVE_ATIVO, true)
                .putLong(CHAVE_ANCORA, agoraMs)
                .putInt(CHAVE_ENVIADOS, 0)
                .apply();
    }

    public void desativar() {
        prefs.edit().clear().apply();
    }

    public int enviados() {
        return prefs.getInt(CHAVE_ENVIADOS, 0);
    }

    public long proximoEm(ZoneId zona) {
        if (!ativo()) {
            return -1L;
        }
        return AgendaQuestionario.proximo(enviados(), prefs.getLong(CHAVE_ANCORA, 0L), zona);
    }

    public void registrarEnvio(long agoraMs) {
        prefs.edit()
                .putInt(CHAVE_ENVIADOS, enviados() + 1)
                .putLong(CHAVE_ANCORA, agoraMs)
                .apply();
    }
}
