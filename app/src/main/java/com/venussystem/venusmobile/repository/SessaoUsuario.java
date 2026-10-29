package com.venussystem.venusmobile.repository;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.IOException;

public interface SessaoUsuario {
    @Nullable
    String uid();

    @Nullable
    String nome();

    @Nullable
    String email();

    @WorkerThread
    @Nullable
    String token(boolean forcarRenovacao) throws IOException;
}
