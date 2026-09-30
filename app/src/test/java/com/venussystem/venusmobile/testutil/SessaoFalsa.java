package com.venussystem.venusmobile.testutil;

import androidx.annotation.Nullable;

import com.venussystem.venusmobile.repository.SessaoUsuario;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Sessao do Firebase de mentira: o FirebaseAuth nao sobe no Robolectric, e os
 * testes precisam controlar UID, nome e cada token entregue.
 */
public final class SessaoFalsa implements SessaoUsuario {

    public String uid = "uid-ana";
    public String nome = "Ana Souza";
    public String email = "ana@venus.com";

    /** Token normal, e o que vem quando se pede renovacao. */
    public String token = "token-1";
    public String tokenRenovado = "token-2";

    /** Quando preenchido, token() falha como se estivesse sem rede. */
    public IOException falhaDeRede;

    /**
     * Cada chamada de token(), com o valor de forcarRenovacao. Thread-safe
     * porque quem pede o token e o executor do repositorio, nao o teste.
     */
    public final List<Boolean> pedidosDeToken = new CopyOnWriteArrayList<>();

    @Nullable
    @Override
    public String uid() {
        return uid;
    }

    @Nullable
    @Override
    public String nome() {
        return nome;
    }

    @Nullable
    @Override
    public String email() {
        return email;
    }

    @Nullable
    @Override
    public String token(boolean forcarRenovacao) throws IOException {
        pedidosDeToken.add(forcarRenovacao);
        if (falhaDeRede != null) {
            throw falhaDeRede;
        }
        return forcarRenovacao ? tokenRenovado : token;
    }
}
