package com.venussystem.venusmobile.view.util;

import android.content.Context;
import android.net.Uri;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * Copia de imagens escolhidas (galeria ou camera) para o armazenamento
 * privado do app, para a capa de uma lista sobreviver ao reinicio do
 * aparelho. Um content:// da galeria pode perder o acesso depois que o
 * processo morre, e o arquivo temporario da camera fica no cache, que o
 * sistema pode limpar - por isso a copia final vai sempre para filesDir.
 */
public final class ImagemLocalUtil {

    private static final String PASTA_CAPAS = "capas_lista";
    private static final String PASTA_CAPTURAS = "capturas";

    private ImagemLocalUtil() {
    }

    public static String salvarCopiaPersistente(Context context, Uri origem) throws IOException {
        File pasta = new File(context.getFilesDir(), PASTA_CAPAS);
        if (!pasta.exists() && !pasta.mkdirs()) {
            throw new IOException("Nao foi possivel criar a pasta de capas.");
        }
        File destino = new File(pasta, "capa_" + UUID.randomUUID() + ".jpg");

        try (InputStream entrada = context.getContentResolver().openInputStream(origem)) {
            if (entrada == null) {
                throw new IOException("Nao foi possivel abrir a imagem escolhida.");
            }
            try (OutputStream saida = new FileOutputStream(destino)) {
                byte[] buffer = new byte[8192];
                int lidos;
                while ((lidos = entrada.read(buffer)) != -1) {
                    saida.write(buffer, 0, lidos);
                }
            }
        }
        return Uri.fromFile(destino).toString();
    }

    /**
     * Cria um arquivo vazio no cache e devolve o Uri (via FileProvider) para
     * a camera do sistema gravar a foto ali - a camera precisa de um Uri
     * content:// pra escrever, nao aceita um caminho de arquivo direto.
     */
    public static Uri criarUriParaCaptura(Context context) throws IOException {
        File pasta = new File(context.getCacheDir(), PASTA_CAPTURAS);
        if (!pasta.exists() && !pasta.mkdirs()) {
            throw new IOException("Nao foi possivel criar a pasta de capturas.");
        }
        File arquivo = File.createTempFile("captura_", ".jpg", pasta);
        return FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", arquivo);
    }
}
