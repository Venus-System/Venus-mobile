package com.venussystem.venusmobile.testutil;

import androidx.annotation.Nullable;

import com.venussystem.venusmobile.repository.SessaoUsuario;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class SessaoFalsa implements SessaoUsuario {

    public String uid = "uid-ana";
    public String nome = "Ana Souza";
    public String email = "ana@venus.com";

    public String token = "token-1";
    public String tokenRenovado = "token-2";

    public IOException falhaDeRede;

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
