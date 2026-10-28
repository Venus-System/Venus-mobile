package com.venussystem.venusmobile.repository;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.core.content.ContextCompat;

import com.venussystem.venusmobile.repository.api.ClienteApi;
import com.venussystem.venusmobile.repository.api.VenusApi;
import com.venussystem.venusmobile.repository.api.dto.UserLocationRequest;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

import retrofit2.Response;

public class LocalizacaoRepository {

    static final String ARQUIVO = "venus_localizacao";
    private static final String CHAVE_PEDIU = "pediu_permissao";
    private static final String CHAVE_ENVIADO_EM = "enviado_em";

    @VisibleForTesting
    static final long VALIDADE_MS = TimeUnit.DAYS.toMillis(7);

    private static final int PROIBIDO = 403;
    private static final double ESCALA_ARREDONDAMENTO = 100.0;

    private final SharedPreferences prefs;
    private final UsuarioApiRepository usuarios;
    private final VenusApi api;
    private final LeitorLocalizacao leitor;
    private final BooleanSupplier temPermissao;
    private final LongSupplier relogio;

    public LocalizacaoRepository(Context context) {
        this(DadosDaConta.prefs(context, ARQUIVO), new UsuarioApiRepository(context),
                ClienteApi.get(), new LeitorLocalizacaoGoogle(context),
                () -> permissaoConcedida(context), System::currentTimeMillis);
    }

    @VisibleForTesting
    public LocalizacaoRepository(SharedPreferences prefs, UsuarioApiRepository usuarios,
                                 VenusApi api, LeitorLocalizacao leitor,
                                 BooleanSupplier temPermissao, LongSupplier relogio) {
        this.prefs = prefs;
        this.usuarios = usuarios;
        this.api = api;
        this.leitor = leitor;
        this.temPermissao = temPermissao;
        this.relogio = relogio;
    }

    public boolean temPermissao() {
        return temPermissao.getAsBoolean();
    }

    public boolean jaPediuPermissao() {
        return prefs.getBoolean(CHAVE_PEDIU, false);
    }

    public void marcarPediuPermissao() {
        prefs.edit().putBoolean(CHAVE_PEDIU, true).apply();
    }

    @VisibleForTesting
    boolean precisaEnviar() {
        long enviadoEm = prefs.getLong(CHAVE_ENVIADO_EM, 0L);
        return enviadoEm == 0L || relogio.getAsLong() - enviadoEm >= VALIDADE_MS;
    }

    public void enviarEmSegundoPlano() {
        SincronizacaoRepository.EXECUTOR.execute(this::enviarAgora);
    }

    @WorkerThread
    @VisibleForTesting
    public boolean enviarAgora() {
        if (!temPermissao() || !precisaEnviar()) {
            return false;
        }
        Long userId = usuarios.obterIdSincrono();
        if (userId == null) {
            return false;
        }
        LeitorLocalizacao.Posicao posicao = leitor.ler();
        if (posicao == null) {
            return false;
        }

        UserLocationRequest corpo = new UserLocationRequest();
        corpo.latitude = posicao.latitude;
        corpo.longitude = posicao.longitude;
        corpo.city = posicao.cidade;
        corpo.state = posicao.estado;

        try {
            Response<Void> resposta = api.atualizarLocalizacao(userId, corpo).execute();
            if (resposta.code() == PROIBIDO) {
                usuarios.esquecerId();
                return false;
            }
            if (!resposta.isSuccessful()) {
                return false;
            }
            prefs.edit().putLong(CHAVE_ENVIADO_EM, relogio.getAsLong()).apply();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    static double arredondar(double valor) {
        return Math.round(valor * ESCALA_ARREDONDAMENTO) / ESCALA_ARREDONDAMENTO;
    }

    private static boolean permissaoConcedida(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }
}
