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
