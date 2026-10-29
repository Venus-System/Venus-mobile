package com.venussystem.venusmobile.repository;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GetTokenResult;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class SessaoFirebase implements SessaoUsuario {
    private static final long LIMITE_TOKEN_SEGUNDOS = 30L;

    private final FirebaseAuth auth = FirebaseAuth.getInstance();

    @Nullable
    @Override
    public String uid() {
        FirebaseUser usuario = auth.getCurrentUser();
        return usuario == null ? null : usuario.getUid();
    }

    @Nullable
    @Override
    public String nome() {
        FirebaseUser usuario = auth.getCurrentUser();
        return usuario == null ? null : usuario.getDisplayName();
    }

    @Nullable
    @Override
    public String email() {
        FirebaseUser usuario = auth.getCurrentUser();
        return usuario == null ? null : usuario.getEmail();
    }

    @WorkerThread
    @Nullable
    @Override
    public String token(boolean forcarRenovacao) throws IOException {
        FirebaseUser usuario = auth.getCurrentUser();
        if (usuario == null) {
            return null;
        }
        try {
            GetTokenResult resultado = Tasks.await(usuario.getIdToken(forcarRenovacao),
                    LIMITE_TOKEN_SEGUNDOS, TimeUnit.SECONDS);
            return resultado.getToken();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof FirebaseNetworkException) {
                throw new IOException("Sem conexao para renovar o token.", e.getCause());
            }
            return null;
        } catch (TimeoutException e) {
            throw new IOException("O Firebase demorou para entregar o token.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Pedido do token interrompido.", e);
        }
    }
}
