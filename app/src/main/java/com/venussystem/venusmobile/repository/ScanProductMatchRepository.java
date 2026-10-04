package com.venussystem.venusmobile.repository;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import com.venussystem.venusmobile.model.Produto;
import com.venussystem.venusmobile.model.ScanFrontData;
import com.venussystem.venusmobile.model.ScanProductMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import static com.venussystem.venusmobile.repository.ScanCatalogMatcher.procurarMelhorProduto;

/**
 * Identifica um produto da VENUS usando os dados extraídos
 * da frente da embalagem e o catálogo real carregado.
 *
 * Fluxo:
 *
 * OCR
 *   ↓
 * marca
 *   ↓
 * produtos da marca
 *   ↓
 * tokens/frases relevantes do produto
 *   ↓
 * produto real do catálogo
 */
public class ScanProductMatchRepository {

    private static final long MAX_WAIT_MILLIS = 15000L;

    private static final long WAIT_STEP_MILLIS = 200L;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    private final ProdutoRepository produtoRepository =
            new ProdutoRepository();

    public interface Callback {
        void onSuccess(
                @NonNull ScanProductMatch resultado
        );
        void onError(
                @NonNull Exception exception
        );
    }

    public void identificar(
            @NonNull ScanFrontData frontData,
            @NonNull Callback callback
    ) {
        executor.execute(() -> {
            try {
                List<Produto> catalogo =
                        obterCatalogo();
                if (catalogo == null
                        || catalogo.isEmpty()) {
                    postarResultado(
                            callback,
                            ScanProductMatch.semMatch()
                    );
                    return;
                }
                ScanProductMatch resultado =
                        procurarMelhorProduto(
                                frontData,
                                catalogo
                        );
                postarResultado(
                        callback,
                        resultado
                );
            } catch (Exception exception) {
                Log.e(
                        TAG,
                        "ERRO AO IDENTIFICAR PRODUTO",
                        exception
                );
                mainHandler.post(
                        () ->
                                callback.onError(
                                        exception
                                )
                );
            }
        });
    }

    @NonNull
    private List<Produto> obterCatalogo()
            throws InterruptedException {
        List<Produto> catalogo =
                produtoRepository
                        .getCatalogo()
                        .getValue();
        if (catalogo != null
                && !catalogo.isEmpty()) {
            return catalogo;
        }
        produtoRepository.carregar(
                false
        );
        long inicio =
                System.currentTimeMillis();
        while (
                System.currentTimeMillis()
                        - inicio
                        < MAX_WAIT_MILLIS
        ) {
            catalogo =
                    produtoRepository
                            .getCatalogo()
                            .getValue();
            if (catalogo != null
                    && !catalogo.isEmpty()) {
                return catalogo;
            }
            Thread.sleep(
                    WAIT_STEP_MILLIS
            );
        }
        catalogo =
                produtoRepository
                        .getCatalogo()
                        .getValue();
        return catalogo != null
                ? catalogo
                : new ArrayList<>();
    }

    private void postarResultado(
            @NonNull Callback callback,
            @NonNull ScanProductMatch resultado
    ) {
        mainHandler.post(
                () ->
                        callback.onSuccess(
                                resultado
                        )
        );
    }

    public void close() {
        executor.shutdownNow();
    }
}
