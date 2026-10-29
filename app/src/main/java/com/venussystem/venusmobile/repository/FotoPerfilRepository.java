package com.venussystem.venusmobile.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.core.content.ContextCompat;

import com.venussystem.venusmobile.repository.api.ClienteApi;
import com.venussystem.venusmobile.repository.api.VenusApi;
import com.venussystem.venusmobile.repository.api.dto.MediaAssetResponse;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Response;

public class FotoPerfilRepository {

    public interface AoTerminar {
        void aoTerminar(@Nullable String caminho);
    }

    static final String ARQUIVO = "venus_foto_perfil";
    private static final String CHAVE_CAMINHO = "caminho";
    private static final String CHAVE_PENDENTE = "pendente";
    private static final String PASTA = "fotos_perfil";

    private static final MediaType JPEG = MediaType.get("image/jpeg");
    private static final String CAMPO_ARQUIVO = "file";

    private static final int PROIBIDO = 403;
    private static final int NAO_ENCONTRADO = 404;

    private static final ExecutorService PREPARO = Executors.newSingleThreadExecutor();
    private static final ExecutorService REDE = Executors.newSingleThreadExecutor();

    private static final Object TRAVA = new Object();

    private final Context context;
    private final SharedPreferences prefs;
    private final UsuarioApiRepository usuarios;
    private final VenusApi api;
    private final Executor principal;

    public FotoPerfilRepository(Context context) {
        this(context, DadosDaConta.uidAtual(context), new UsuarioApiRepository(context),
                ClienteApi.get(), ContextCompat.getMainExecutor(context));
    }

    @VisibleForTesting
    public FotoPerfilRepository(Context context, @Nullable String uid, UsuarioApiRepository usuarios,
                                VenusApi api, Executor principal) {
        this.context = context.getApplicationContext();
        this.prefs = DadosDaConta.prefs(context, ARQUIVO, uid);
        this.usuarios = usuarios;
        this.api = api;
        this.principal = principal;
    }

    @Nullable
    public String caminho() {
        return prefs.getString(CHAVE_CAMINHO, null);
    }

    public void trocar(Uri origem, AoTerminar aoTerminar) {
        PREPARO.execute(() -> {
            String caminho;
            try {
                caminho = salvarReduzida(origem);
                guardarNova(caminho);
            } catch (IOException | RuntimeException e) {
                caminho = null;
            }
            String resultado = caminho;
            principal.execute(() -> aoTerminar.aoTerminar(resultado));
        });
    }

    public void enviarEmSegundoPlano() {
        REDE.execute(this::enviarAgora);
    }

    @WorkerThread
    @VisibleForTesting
    public boolean enviarAgora() {
        if (!prefs.getBoolean(CHAVE_PENDENTE, false)) {
            return false;
        }
        String caminho = caminho();
        File arquivo = arquivoLocal(caminho);
        if (arquivo == null || !arquivo.exists()) {
            marcarEnviada(caminho);
            return false;
        }
        Long userId = usuarios.obterIdSincrono();
        if (userId == null) {
            return false;
        }

        try {
            RequestBody corpo = RequestBody.create(arquivo, JPEG);
            MultipartBody.Part parte =
                    MultipartBody.Part.createFormData(CAMPO_ARQUIVO, arquivo.getName(), corpo);
            Response<Void> resposta = api.enviarAvatar(userId, parte).execute();
            int codigo = resposta.code();

            if (resposta.isSuccessful()) {
                marcarEnviada(caminho);
                return true;
            }
            if (codigo == 400 || codigo == 413 || codigo == 422) {
                marcarEnviada(caminho);
                return false;
            }
            if (codigo == PROIBIDO || codigo == NAO_ENCONTRADO) {
                usuarios.esquecerId();
            }
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    public void buscarDaApiEmSegundoPlano(AoTerminar aoAchar) {
        REDE.execute(() -> {
            String link = buscarDaApiAgora();
            if (link != null) {
                principal.execute(() -> aoAchar.aoTerminar(link));
            }
        });
    }

    @WorkerThread
    @VisibleForTesting
    @Nullable
    public String buscarDaApiAgora() {
        if (arquivoLocal(caminho()) != null) {
            return null;
        }
        Long userId = usuarios.obterIdSincrono();
        if (userId == null) {
            return null;
        }

        Response<MediaAssetResponse> resposta;
        try {
            resposta = api.buscarAvatar(userId).execute();
        } catch (IOException e) {
            return null;
        }
        MediaAssetResponse foto = resposta.body();
        if (!resposta.isSuccessful() || foto == null || foto.url == null || foto.url.isEmpty()) {
            return null;
        }

        synchronized (TRAVA) {
            String atual = caminho();
            if (arquivoLocal(atual) != null || foto.url.equals(atual)) {
                return null;
            }
            prefs.edit().putString(CHAVE_CAMINHO, foto.url).apply();
            return foto.url;
        }
    }

    @VisibleForTesting
    void guardarNova(String caminho) {
        synchronized (TRAVA) {
            File anterior = arquivoLocal(caminho());
            prefs.edit()
                    .putString(CHAVE_CAMINHO, caminho)
                    .putBoolean(CHAVE_PENDENTE, true)
                    .apply();
            if (anterior != null && !anterior.equals(arquivoLocal(caminho))) {
                anterior.delete();
            }
        }
    }

    private void marcarEnviada(@Nullable String caminhoEnviado) {
        synchronized (TRAVA) {
            String atual = caminho();
            if (atual == null ? caminhoEnviado == null : atual.equals(caminhoEnviado)) {
                prefs.edit().putBoolean(CHAVE_PENDENTE, false).apply();
            }
        }
    }

    private String salvarReduzida(Uri origem) throws IOException {
        File pasta = new File(context.getFilesDir(), PASTA);
        if (!pasta.exists() && !pasta.mkdirs()) {
            throw new IOException("Nao foi possivel criar a pasta das fotos de perfil.");
        }
        File destino = new File(pasta, "foto_" + UUID.randomUUID() + ".jpg");
        ImagemReduzida.gravar(context, origem, destino);
        return destino.toURI().toString();
    }

    @Nullable
    private static File arquivoLocal(@Nullable String caminho) {
        return ImagemReduzida.arquivoLocal(caminho);
    }
}
