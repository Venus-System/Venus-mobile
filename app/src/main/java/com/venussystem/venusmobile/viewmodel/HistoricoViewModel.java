package com.venussystem.venusmobile.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.ItemHistorico;
import com.venussystem.venusmobile.model.LinhaHistorico;
import com.venussystem.venusmobile.repository.HistoricoRepository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class HistoricoViewModel extends AndroidViewModel {

    private static final DateTimeFormatter FORMATO =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final HistoricoRepository repository;
    private final MediatorLiveData<List<LinhaHistorico>> linhas = new MediatorLiveData<>();

    public HistoricoViewModel(@NonNull Application application) {
        super(application);
        repository = new HistoricoRepository(application);
        linhas.addSource(repository.getHistorico(), this::montarLinhas);
        repository.carregar();
    }

    public LiveData<List<LinhaHistorico>> getLinhas() {
        return linhas;
    }

    /**
     * Achata as entradas numa lista unica de linhas, inserindo um cabecalho toda
     * vez que a data muda. Como as entradas vem em ordem decrescente de data,
     * basta comparar com a anterior.
     */
    private void montarLinhas(List<ItemHistorico> entradas) {
        List<LinhaHistorico> resultado = new ArrayList<>();
        if (entradas == null) {
            linhas.setValue(resultado);
            return;
        }

        LocalDate dataAnterior = null;
        for (ItemHistorico entrada : entradas) {
            if (!entrada.getData().equals(dataAnterior)) {
                resultado.add(LinhaHistorico.deData(rotular(entrada.getData())));
                dataAnterior = entrada.getData();
            }
            resultado.add(LinhaHistorico.deProduto(entrada.getProduto(), entrada.getStatus()));
        }
        linhas.setValue(resultado);
    }

    private String rotular(LocalDate data) {
        return data.equals(LocalDate.now())
                ? getApplication().getString(R.string.historico_hoje)
                : data.format(FORMATO);
    }
}
