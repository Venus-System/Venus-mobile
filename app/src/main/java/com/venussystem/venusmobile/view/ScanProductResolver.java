package com.venussystem.venusmobile.view;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import java.io.File;

/**
 * Ponto de integração para OCR/READ do rótulo.
 *
 * Por enquanto não inventa um match: devolve null e o fluxo segue para a
 * captura frontal/cadastro. Quando a API estiver pronta, este método pode
 * enviar a foto para OCR, comparar com o catálogo e devolver o ID encontrado.
 */
public final class ScanProductResolver {
    public interface Callback {
        void onResult(@Nullable Long productId);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private ScanProductResolver() {}

    public static void resolver(File labelPhoto, Callback callback) {
        // Mantemos a decisão assíncrona para não travar a UI. O ponto de
        // integração real fica aqui quando o endpoint de OCR/READ existir.
        new Thread(() -> MAIN.post(() -> callback.onResult(null)), "scan-label-resolver").start();
    }
}
