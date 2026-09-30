package com.venussystem.venusmobile.repository;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.IOException;

/**
 * Quem esta logado, do jeito que as chamadas ao servidor precisam: o UID
 * identifica a pessoa no Venus-CRUD, e o token prova para a API de IA que o
 * pedido vem mesmo dela.
 *
 * E uma interface so para os testes poderem trocar o Firebase por uma sessao
 * falsa - o FirebaseAuth nao sobe dentro do Robolectric.
 */
public interface SessaoUsuario {

    /** Null quando ninguem esta logado. */
    @Nullable
    String uid();

    @Nullable
    String nome();

    @Nullable
    String email();

    /**
     * Token de ID do Firebase, o que vai no "Authorization: Bearer".
     *
     * Espera o Firebase responder, entao nunca pode rodar na thread principal.
     *
     * @param forcarRenovacao true pede um token novo mesmo que o guardado ainda
     *                        pareca valido - usado quando a API recusou o atual.
     * @return null quando nao ha sessao valida (ninguem logado, conta desativada
     * ou revogada): quem chama deve tratar como sessao expirada.
     * @throws IOException quando nao deu para falar com o Firebase (sem rede ou
     *                     demorou demais) - nao e problema da sessao.
     */
    @WorkerThread
    @Nullable
    String token(boolean forcarRenovacao) throws IOException;
}
