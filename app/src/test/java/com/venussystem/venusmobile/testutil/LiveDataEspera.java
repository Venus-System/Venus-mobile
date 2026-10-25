package com.venussystem.venusmobile.testutil;

import android.os.Looper;

import androidx.lifecycle.LiveData;

import java.util.concurrent.CountDownLatch;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import static org.robolectric.Shadows.shadowOf;

public final class LiveDataEspera {

    public static final long TIMEOUT_PADRAO_MS = 15000L;

    private LiveDataEspera() {
    }

    /**
     * Espera um LiveData satisfazer uma condicao lendo getValue() ate dar certo.
     *
     * Nao registra Observer de proposito: com o InstantTaskExecutorRule o
     * repositorio entrega o valor na propria thread de fundo, e um
     * removeObserver() na thread do teste ao mesmo tempo dava, as vezes,
     * ConcurrentModificationException dentro do LiveData (SafeIterableMap).
     * Todas as condicoes usadas sao estados finais (erro, catalogo pronto,
     * carregando false), entao ler o valor atual basta.
     */
    public static <T> void aguardarValor(LiveData<T> liveData, Predicate<T> condicao) {
        aguardarValor(liveData, condicao, TIMEOUT_PADRAO_MS);
    }

    public static <T> void aguardarValor(LiveData<T> liveData, Predicate<T> condicao, long timeoutMs) {
        long prazo = System.currentTimeMillis() + timeoutMs;
        while (!condicao.test(liveData.getValue())) {
            if (System.currentTimeMillis() >= prazo) {
                throw new AssertionError("Timeout esperando LiveData satisfazer condicao.");
            }
            try {
                Thread.sleep(2L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrompido esperando LiveData.", e);
            }
        }
    }

    /**
     * Espera uma condicao generica que nao depende de LiveData nem de Looper
     * (ex.: contador do MockWebServer). Sem idle(), so um spin leve.
     */
    public static void aguardarAte(BooleanSupplier condicao) {
        aguardarAte(condicao, TIMEOUT_PADRAO_MS);
    }

    public static void aguardarAte(BooleanSupplier condicao, long timeoutMs) {
        long prazo = System.currentTimeMillis() + timeoutMs;
        do {
            if (condicao.getAsBoolean()) {
                return;
            }
            if (System.currentTimeMillis() >= prazo) {
                throw new AssertionError("Timeout esperando condicao ficar verdadeira.");
            }
            Thread.yield();
        } while (true);
    }

    /**
     * Espera um callback que chega via Handler(Looper.getMainLooper()) real
     * (nao passa pelo ArchTaskExecutor/InstantTaskExecutorRule). Com o
     * Robolectric em modo PAUSED, e preciso girar o Looper manualmente.
     */
    public static void aguardarLatch(CountDownLatch latch, long timeoutMs) {
        long prazo = System.currentTimeMillis() + timeoutMs;
        do {
            shadowOf(Looper.getMainLooper()).idle();
            if (latch.getCount() == 0) {
                return;
            }
            if (System.currentTimeMillis() >= prazo) {
                throw new AssertionError("Timeout esperando latch.");
            }
            Thread.yield();
        } while (true);
    }
}
