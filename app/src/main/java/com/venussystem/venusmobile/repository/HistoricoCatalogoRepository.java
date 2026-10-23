package com.venussystem.venusmobile.repository;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.venussystem.venusmobile.model.ItemHistorico;
import com.venussystem.venusmobile.model.Produto;

import java.lang.reflect.Type;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Persiste localmente os produtos do catálogo acessados pelo usuário. */
public final class HistoricoCatalogoRepository {

    private static final String ARQUIVO = "venus_historico_catalogo";
    private static final String CHAVE_HISTORICO = "produtos_acessados";
    private static final int LIMITE = 20;
    private static final Type TIPO_REGISTROS = new TypeToken<List<Registro>>() { }.getType();
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    private final SharedPreferences preferencias;
    private final Gson gson = new Gson();
    private final MutableLiveData<List<ItemHistorico>> historico = new MutableLiveData<>();

    public HistoricoCatalogoRepository(@NonNull Context context) {
        preferencias = DadosDaConta.prefs(context, ARQUIVO);
    }

    @VisibleForTesting
    HistoricoCatalogoRepository(@NonNull SharedPreferences preferencias) {
        this.preferencias = preferencias;
    }

    public LiveData<List<ItemHistorico>> getHistorico() {
        return historico;
    }

    public void carregar() {
        EXECUTOR.execute(() -> historico.postValue(mapear(lerRegistros())));
    }

    /** Coloca o produto no topo e remove uma entrada anterior do mesmo produto. */
    public synchronized void registrarAcesso(@Nullable Produto produto) {
        if (produto == null || produto.getId() == null || produto.getId() <= 0) {
            return;
        }

        List<Registro> registros = lerRegistros();
        Iterator<Registro> iterator = registros.iterator();
        while (iterator.hasNext()) {
            Registro registro = iterator.next();
            if (registro != null && produto.getId().toString().equals(registro.id)) {
                iterator.remove();
            }
        }

        registros.add(0, Registro.de(produto));
        if (registros.size() > LIMITE) {
            registros.subList(LIMITE, registros.size()).clear();
        }
        preferencias.edit().putString(CHAVE_HISTORICO, gson.toJson(registros)).apply();
        historico.postValue(mapear(registros));
    }

    @NonNull
    private synchronized List<Registro> lerRegistros() {
        String json = preferencias.getString(CHAVE_HISTORICO, "");
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            List<Registro> registros = gson.fromJson(json, TIPO_REGISTROS);
            return registros == null ? new ArrayList<>() : new ArrayList<>(registros);
        } catch (RuntimeException ignored) {
            return new ArrayList<>();
        }
    }

    @NonNull
    private static List<ItemHistorico> mapear(@Nullable List<Registro> registros) {
        if (registros == null || registros.isEmpty()) {
            return Collections.emptyList();
        }
        List<ItemHistorico> resultado = new ArrayList<>();
        for (Registro registro : registros) {
            if (registro == null || registro.id == null || registro.name == null) {
                continue;
            }
            try {
                long id = Long.parseLong(registro.id);
                if (id <= 0) continue;
                Produto produto = new Produto(
                        id,
                        registro.name,
                        registro.brandName,
                        registro.overallScore,
                        registro.imageUrl,
                        registro.categoryId,
                        registro.categoryName
                );
                resultado.add(new ItemHistorico(produto, data(registro.accessedAt)));
            } catch (RuntimeException ignored) {
                // Uma entrada corrompida não impede a leitura das demais.
            }
        }
        return resultado;
    }

    @NonNull
    private static LocalDate data(@Nullable String valor) {
        try {
            return valor == null ? LocalDate.now() : LocalDate.parse(valor);
        } catch (RuntimeException ignored) {
            return LocalDate.now();
        }
    }

    private static final class Registro {
        private String id;
        private String name;
        private String brandName;
        private Integer overallScore;
        private String imageUrl;
        private Long categoryId;
        private String categoryName;
        private String accessedAt;

        private static Registro de(@NonNull Produto produto) {
            Registro registro = new Registro();
            registro.id = produto.getId().toString();
            registro.name = produto.getName();
            registro.brandName = produto.getBrandName();
            registro.overallScore = produto.getOverallScore();
            registro.imageUrl = produto.getImageUrl();
            registro.categoryId = produto.getCategoryId();
            registro.categoryName = produto.getCategoryName();
            registro.accessedAt = LocalDate.now().toString();
            return registro;
        }
    }
}
