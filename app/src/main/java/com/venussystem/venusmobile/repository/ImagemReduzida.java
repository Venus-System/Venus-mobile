package com.venussystem.venusmobile.repository;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;

/**
 * Fotos que vao para a API (foto de perfil e capa de lista) sobem reduzidas.
 *
 * Uma foto de camera passa de 4000 px e de alguns MB. A API limita tamanho e
 * dimensao (configurados no servidor), e as duas aparecem pequenas na tela:
 * 1024 px no lado maior sobra e fica em poucas centenas de KB.
 */
final class ImagemReduzida {

    private static final int LADO_MAXIMO = 1024;
    private static final int QUALIDADE_JPEG = 85;
    private static final String PASTA_ENVIO = "envio_imagens";

    private ImagemReduzida() {
    }

    /**
     * Abre a imagem ja do tamanho final e grava como JPEG. O ImageDecoder
     * tambem aplica a rotacao que a camera grava no EXIF: sem isso, uma foto
     * tirada em pe apareceria deitada.
     */
    static void gravar(Context context, Uri origem, File destino) throws IOException {
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

        try (OutputStream saida = new FileOutputStream(destino)) {
            if (!foto.compress(Bitmap.CompressFormat.JPEG, QUALIDADE_JPEG, saida)) {
                throw new IOException("Nao foi possivel gravar a imagem reduzida.");
            }
        } finally {
            foto.recycle();
        }
    }

    /**
     * Copia reduzida, no cache, de uma imagem guardada no aparelho em tamanho
     * original (a capa de lista). Quem envia apaga a copia depois.
     */
    static File copiaParaEnvio(Context context, File original) throws IOException {
        File pasta = new File(context.getCacheDir(), PASTA_ENVIO);
        if (!pasta.exists() && !pasta.mkdirs()) {
            throw new IOException("Nao foi possivel criar a pasta de envio.");
        }
        File copia = File.createTempFile("capa_", ".jpg", pasta);
        try {
            gravar(context, Uri.fromFile(original), copia);
        } catch (IOException | RuntimeException e) {
            copia.delete();
            throw e;
        }
        return copia;
    }

    /**
     * O arquivo de uma imagem guardada no aparelho; null para link da API ou
     * sem imagem. Usa java.net.URI (par do File.toURI) para ida e volta darem
     * o mesmo arquivo em qualquer sistema - inclusive nos testes no Windows.
     * Le tambem o "file:///..." que o Uri.fromFile grava no aparelho.
     */
    @Nullable
    static File arquivoLocal(@Nullable String caminho) {
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
