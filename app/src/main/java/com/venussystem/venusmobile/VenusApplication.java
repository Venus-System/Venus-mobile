package com.venussystem.venusmobile;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

import com.venussystem.venusmobile.repository.DadosDaConta;

/**
 * Trava o app no modo claro, e da destino aos dados guardados antes de o app
 * separar os dados por conta.
 *
 * O app nao tem design escuro: todo layout fixa fundo branco e texto escuro.
 * Como o tema declarado e DayNight, num aparelho com modo escuro ligado o
 * sistema entendia que estava no escuro e pintava os icones da barra de status
 * de branco - em cima do nosso fundo branco, ou seja, invisiveis.
 *
 * Nao adianta so trocar o parent do tema para Light: o EdgeToEdge.enable()
 * decide a cor dos icones pelo uiMode da configuracao do aparelho, nao pelo
 * tema. Forcar MODE_NIGHT_NO muda o uiMode do app, entao os icones voltam a
 * ser escuros e os componentes do Material param de alternar de cor.
 */
public class VenusApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        // Antes de qualquer tela abrir: os dados de antes da separacao por
        // conta nao podem aparecer para ninguem (ver DadosDaConta). Aqui, e
        // nao na tela de boas-vindas, porque o Android pode reabrir o app
        // direto numa tela do meio.
        DadosDaConta.migrarDadosAntigos(this);
    }
}
