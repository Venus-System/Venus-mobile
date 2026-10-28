package com.venussystem.venusmobile.repository;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.Tasks;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

public class LeitorLocalizacaoGoogle implements LeitorLocalizacao {

    private static final long ESPERA_POSICAO_S = 15;
    private static final long ESPERA_ULTIMA_S = 5;
    private static final long ESPERA_ENDERECO_S = 5;

    private final Context context;

    public LeitorLocalizacaoGoogle(Context context) {
        this.context = context.getApplicationContext();
    }

    @WorkerThread
    @Nullable
    @Override
    @SuppressLint("MissingPermission")
    public Posicao ler() {
        try {
            FusedLocationProviderClient cliente =
                    LocationServices.getFusedLocationProviderClient(context);
            Location local = Tasks.await(
                    cliente.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null),
                    ESPERA_POSICAO_S, TimeUnit.SECONDS);
            if (local == null) {
                local = Tasks.await(cliente.getLastLocation(), ESPERA_ULTIMA_S, TimeUnit.SECONDS);
            }
            if (local == null) {
                return null;
            }

            double latitude = LocalizacaoRepository.arredondar(local.getLatitude());
            double longitude = LocalizacaoRepository.arredondar(local.getLongitude());
            Address endereco = resolverEndereco(latitude, longitude);
            return new Posicao(latitude, longitude,
                    endereco == null ? null : cidade(endereco),
                    endereco == null ? null : endereco.getAdminArea());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException | TimeoutException | SecurityException e) {
            return null;
        }
    }

    @Nullable
    private static String cidade(Address endereco) {
        String cidade = endereco.getLocality();
        return cidade != null ? cidade : endereco.getSubAdminArea();
    }

    @Nullable
    private Address resolverEndereco(double latitude, double longitude)
            throws InterruptedException {
        if (!Geocoder.isPresent()) {
            return null;
        }
        AtomicReference<Address> achado = new AtomicReference<>();
        CountDownLatch pronto = new CountDownLatch(1);
        new Geocoder(context, new Locale("pt", "BR")).getFromLocation(latitude, longitude, 1,
                new Geocoder.GeocodeListener() {
                    @Override
                    public void onGeocode(List<Address> enderecos) {
                        if (!enderecos.isEmpty()) {
                            achado.set(enderecos.get(0));
                        }
                        pronto.countDown();
                    }

                    @Override
                    public void onError(@Nullable String mensagem) {
                        pronto.countDown();
                    }
                });
        pronto.await(ESPERA_ENDERECO_S, TimeUnit.SECONDS);
        return achado.get();
    }
}
