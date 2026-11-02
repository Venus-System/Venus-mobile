package com.venussystem.venusmobile.repository;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

public interface LeitorLocalizacao {
    @WorkerThread
    @Nullable
    Posicao ler();

    final class Posicao {
        public final double latitude;
        public final double longitude;
        @Nullable
        public final String cidade;
        @Nullable
        public final String estado;

        public Posicao(double latitude, double longitude, @Nullable String cidade,
                       @Nullable String estado) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.cidade = cidade;
            this.estado = estado;
        }
    }
}
