package com.venussystem.venusmobile.repository;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.venussystem.venusmobile.repository.api.dto.ClassificationResponse;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class AnaliseDoPostRepository {

    static final String ARQUIVO = "venus_analise_do_post";
    private static final String PREFIXO = "versao_";

    private static final class Gravada {
        long calculadaEm;
        List<String> ingredientesDeAlergia;
        Integer qualidade;
        Double pontosQualidade;
        Double pontosPerfil;
    }

    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    public AnaliseDoPostRepository(Context context) {
        this(context, DadosDaConta.uidAtual(context));
    }

    @VisibleForTesting
    public AnaliseDoPostRepository(Context context, @Nullable String uid) {
        this.prefs = DadosDaConta.prefs(context, ARQUIVO, uid);
    }

    public void guardar(ClassificationResponse analise) {
        Long calculadaEm = emMilissegundos(analise.calculatedAt);
        if (analise.productVersionId == null || calculadaEm == null) return;
        String chave = PREFIXO + analise.productVersionId;
        List<String> ingredientes = analise.ingredientesDeAlergia();
        ClassificationResponse.Breakdown partes = analise.breakdown;
        boolean temPontos = partes != null && partes.qualityPoints != null;
        if (ingredientes.isEmpty() && !temPontos) {
            prefs.edit().remove(chave).apply();
            return;
        }
        Gravada gravada = new Gravada();
        gravada.calculadaEm = calculadaEm;
        gravada.ingredientesDeAlergia = ingredientes;
        if (temPontos) {
            gravada.qualidade = partes.qualityScore;
            gravada.pontosQualidade = partes.qualityPoints;
            gravada.pontosPerfil = partes.profilePoints;
        }
        prefs.edit().putString(chave, gson.toJson(gravada)).apply();
    }

    public void completar(ClassificationResponse analise) {
        if (analise.productVersionId == null) return;
        Long calculadaEm = emMilissegundos(analise.calculatedAt);
        Gravada gravada = ler(PREFIXO + analise.productVersionId);
        if (calculadaEm == null || gravada == null || gravada.calculadaEm != calculadaEm) return;

        if (analise.ingredientesDeAlergia().isEmpty() && gravada.ingredientesDeAlergia != null
                && !gravada.ingredientesDeAlergia.isEmpty()) {
            ClassificationResponse.Reason alergia = new ClassificationResponse.Reason();
            alergia.source = ClassificationResponse.MOTIVO_ALERGIA;
            alergia.warning = true;
            alergia.ingredientNames = new ArrayList<>(gravada.ingredientesDeAlergia);
            List<ClassificationResponse.Reason> motivos = analise.reasons == null
                    ? new ArrayList<>()
                    : new ArrayList<>(analise.reasons);
            motivos.add(0, alergia);
            analise.reasons = motivos;
        }

        if (gravada.pontosQualidade != null
                && (analise.breakdown == null || analise.breakdown.qualityPoints == null)) {
            if (analise.breakdown == null) analise.breakdown = new ClassificationResponse.Breakdown();
            analise.breakdown.qualityScore = gravada.qualidade;
            analise.breakdown.qualityPoints = gravada.pontosQualidade;
            analise.breakdown.profilePoints = gravada.pontosPerfil;
        }
    }

    public void apagar() {
        prefs.edit().clear().commit();
    }

    @Nullable
    private Gravada ler(String chave) {
        String json = prefs.getString(chave, null);
        if (json == null) return null;
        try {
            return gson.fromJson(json, Gravada.class);
        } catch (JsonParseException e) {
            return null;
        }
    }

    @Nullable
    private static Long emMilissegundos(@Nullable String calculadaEm) {
        if (calculadaEm == null || calculadaEm.trim().isEmpty()) return null;
        try {
            return OffsetDateTime.parse(calculadaEm).toInstant().toEpochMilli();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
