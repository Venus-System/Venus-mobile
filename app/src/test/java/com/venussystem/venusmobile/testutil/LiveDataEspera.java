package com.venussystem.venusmobile.testutil;

import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import static org.robolectric.Shadows.shadowOf;

public final class LiveDataEspera {

    public static final long TIMEOUT_PADRAO_MS = 15000L;

    private LiveDataEspera() {
    }

    /**
     * Espera um LiveData satisfazer uma condicao, sem busy-wait: o
     * InstantTaskExecutorRule faz postValue()/setValue() entregarem pro
     * Observer de forma sincrona (em qualquer thread), entao um
     * CountDownLatch normal e suficiente - nao precisa girar o Looper.
     */
    public static <T> void aguardarValor(LiveData<T> liveData, Predicate<T> condicao) {
        aguardarValor(liveData, condicao, TIMEOUT_PADRAO_MS);
    }

    public static <T> void aguardarValor(LiveData<T> liveData, Predicate<T> condicao, long timeoutMs) {
        if (condicao.test(liveData.getValue())) {
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        Observer<T> observer = valor -> {
            if (condicao.test(valor)) {
                latch.countDown();
            }
        };
        liveData.observeForever(observer);
        try {
            if (condicao.test(liveData.getValue())) {
                return;
            }
            if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                throw new AssertionError("Timeout esperando LiveData satisfazer condicao.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrompido esperando LiveData.", e);
        } finally {
            liveData.removeObserver(observer);
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
