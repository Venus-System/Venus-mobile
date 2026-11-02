package com.venussystem.venusmobile;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

import com.venussystem.venusmobile.repository.DadosDaConta;
import com.venussystem.venusmobile.view.Notificador;

public class VenusApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        DadosDaConta.migrarDadosAntigos(this);

        Notificador.criarCanais(this);
    }
}
