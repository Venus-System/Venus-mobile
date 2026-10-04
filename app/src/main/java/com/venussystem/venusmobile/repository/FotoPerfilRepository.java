package com.venussystem.venusmobile.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.core.content.ContextCompat;

import com.venussystem.venusmobile.repository.api.ClienteApi;
import com.venussystem.venusmobile.repository.api.VenusApi;
import com.venussystem.venusmobile.repository.api.dto.MediaAssetResponse;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Response;

/**
 * A foto de perfil da pessoa.
 *
 * A foto escolhida vira uma copia reduzida no aparelho, guardada por conta (ver
 * DadosDaConta): a tela mostra essa copia na hora, sem esperar a rede. Depois
 * ela vai para POST /api/users/{id}/avatar em segundo plano e, se falhar, fica
 * pendente para a proxima abertura do app.
 *
 * Sem foto no aparelho (outro celular, app reinstalado, foto posta pelo site),
 * o perfil pergunta a API e guarda o link que vier.
 */
public class FotoPerfilRepository {

    public interface AoTerminar {
        /** @param caminho o file:// ou o link da foto; null se nao deu para usar a imagem. */
        void aoTerminar(@Nullable String caminho);
    }

    static final String ARQUIVO = "venus_foto_perfil";
    private static final String CHAVE_CAMINHO = "caminho";
    private static final String CHAVE_PENDENTE = "pendente";
    private static final String PASTA = "fotos_perfil";

    // Uma foto de camera passa de 4000 px e de alguns MB. A API limita tamanho
    // e dimensao (configurados no servidor), e o avatar aparece com 128dp:
    // 1024 px no lado maior sobra para a tela e fica em poucas centenas de KB.
    private static final int LADO_MAXIMO = 1024;
    private static final int QUALIDADE_JPEG = 85;

    private static final MediaType JPEG = MediaType.get("image/jpeg");
    private static final String CAMPO_ARQUIVO = "file";

    private static final int PROIBIDO = 403;
    private static final int NAO_ENCONTRADO = 404;

    // Duas filas: preparar a foto (abrir e reduzir) nao pode esperar o envio,
    // que pode estar parado no cold start do Render - a pessoa quer ver a foto
    // nova na hora. Cada fila tem uma thread so, entao dois envios da foto
    // nunca correm juntos.
    private static final ExecutorService PREPARO = Executors.newSingleThreadExecutor();
    private static final ExecutorService REDE = Executors.newSingleThreadExecutor();

    // A tela grava a foto nova e o envio marca o fim, cada um na sua thread.
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

    /** O que mostrar agora: a copia no aparelho, o link da API, ou null se nao tem foto. */
    @Nullable
    public String caminho() {
        return prefs.getString(CHAVE_CAMINHO, null);
    }

    /**
     * Reduz e guarda a imagem escolhida (galeria ou camera) e avisa na thread
     * principal. Quem chama manda enviar para a API depois.
     */
    public void trocar(Uri origem, AoTerminar aoTerminar) {
        PREPARO.execute(() -> {
            String caminho;
            try {
                caminho = salvarReduzida(origem);
                guardarNova(caminho);
            } catch (IOException | RuntimeException e) {
                // Imagem corrompida ou num formato que o Android nao abre.
                caminho = null;
            }
            String resultado = caminho;
            principal.execute(() -> aoTerminar.aoTerminar(resultado));
        });
    }

    public void enviarEmSegundoPlano() {
        REDE.execute(this::enviarAgora);
    }

    /**
     * @return true se a foto foi para a API nesta chamada.
     */
    @WorkerThread
    @VisibleForTesting
    public boolean enviarAgora() {
        if (!prefs.getBoolean(CHAVE_PENDENTE, false)) {
            return false;
        }
        String caminho = caminho();
        File arquivo = arquivoLocal(caminho);
        if (arquivo == null || !arquivo.exists()) {
            // O arquivo sumiu (limpeza do sistema): nao ha o que mandar.
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
                // A API recusou esta imagem (tipo, tamanho ou dimensao). Mandar
                // a mesma de novo nao adianta: ela fica so no aparelho.
                marcarEnviada(caminho);
                return false;
            }
            if (codigo == PROIBIDO || codigo == NAO_ENCONTRADO) {
                // O id guardado nao e mais desta pessoa (banco recriado): a
                // proxima tentativa busca o cadastro de novo.
                usuarios.esquecerId();
            }
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Sem foto no aparelho, pergunta a API e avisa na thread principal so se
     * achou uma foto nova.
     */
    public void buscarDaApiEmSegundoPlano(AoTerminar aoAchar) {
        REDE.execute(() -> {
            String link = buscarDaApiAgora();
            if (link != null) {
                principal.execute(() -> aoAchar.aoTerminar(link));
            }
        });
    }

    /**
     * @return o link da foto na API, se mudou; null se nao tem foto la, se
     * nao mudou, ou se ja existe uma foto escolhida neste aparelho.
     */
    @WorkerThread
    @VisibleForTesting
    @Nullable
    public String buscarDaApiAgora() {
        // A foto escolhida aqui e a mais nova: a da API e a anterior a ela, ou
        // a mesma, ja enviada.
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
            // Uma foto escolhida enquanto a API respondia vale mais que a de la.
            if (arquivoLocal(atual) != null || foto.url.equals(atual)) {
                return null;
            }
            prefs.edit().putString(CHAVE_CAMINHO, foto.url).apply();
            return foto.url;
        }
    }

    /** Guarda a foto nova como a atual e apaga o arquivo da anterior. */
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

    /** So desmarca se a foto continua a mesma: uma trocada no meio do envio segue pendente. */
    private void marcarEnviada(@Nullable String caminhoEnviado) {
        synchronized (TRAVA) {
            String atual = caminho();
            if (atual == null ? caminhoEnviado == null : atual.equals(caminhoEnviado)) {
                prefs.edit().putBoolean(CHAVE_PENDENTE, false).apply();
            }
        }
    }

    /**
     * Abre a imagem ja do tamanho final e grava como JPEG. O ImageDecoder
     * tambem aplica a rotacao que a camera grava no EXIF: sem isso, uma foto
     * tirada em pe apareceria deitada.
     */
    private String salvarReduzida(Uri origem) throws IOException {
        ImageDecoder.Source fonte = ImageDecoder.createSource(context.getContentResolver(), origem);
        Bitmap foto = ImageDecoder.decodeBitmap(fonte, (decoder, info, src) -> {
            int largura = info.getSize().getWidth();
            int altura = info.getSize().getHeight();
            int maior = Math.max(largura, altura);
            if (maior > LADO_MAXIMO) {
                float fator = (float) LADO_MAXIMO / maior;
                decoder.setTargetSize(Math.round(largura * fator), Math.round(altura * fator));
            }
            // Bitmap de hardware nao pode ser comprimido para JPEG.
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
        });

        File pasta = new File(context.getFilesDir(), PASTA);
        if (!pasta.exists() && !pasta.mkdirs()) {
            throw new IOException("Nao foi possivel criar a pasta das fotos de perfil.");
        }
        File destino = new File(pasta, "foto_" + UUID.randomUUID() + ".jpg");
        try (OutputStream saida = new FileOutputStream(destino)) {
            if (!foto.compress(Bitmap.CompressFormat.JPEG, QUALIDADE_JPEG, saida)) {
                throw new IOException("Nao foi possivel gravar a foto de perfil.");
            }
        } finally {
            foto.recycle();
        }
        return destino.toURI().toString();
    }

    /**
     * O arquivo de uma foto escolhida neste aparelho; null para link da API ou
     * sem foto. Usa java.net.URI (par do File.toURI) para ida e volta darem o
     * mesmo arquivo em qualquer sistema - inclusive nos testes no Windows.
     */
    @Nullable
    private static File arquivoLocal(@Nullable String caminho) {
        if (caminho == null || !caminho.startsWith("file:")) {
            return null;
        }
        try {
            return new File(URI.create(caminho));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
