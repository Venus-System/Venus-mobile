package com.venussystem.venusmobile.view.util;

import android.content.Context;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import coil.Coil;
import coil.ImageLoader;
import coil.request.ImageRequest;

import com.venussystem.venusmobile.model.Produto;
import com.venussystem.venusmobile.repository.ProdutoRepository;

/** Centraliza a leitura da foto ativa de media_assets nos cards do app. */
public final class ProdutoImagemLoader {

    private ProdutoImagemLoader() {
    }

    public static void carregar(
            Context context,
            ImageView imagem,
            @Nullable Produto produto,
            @DrawableRes int placeholder
    ) {
        Context appContext = context.getApplicationContext();
        Long produtoId = produto == null ? null : produto.getId();
        String urlLegada = produto == null ? null : produto.getImageUrl();
        imagem.setImageResource(placeholder);
        imagem.setTag(produtoId);

        if (produtoId == null || produtoId <= 0) {
            carregarUrl(appContext, imagem, urlLegada, placeholder);
            return;
        }

        new ProdutoRepository().buscarImagemProduto(produtoId, url -> {
            Object tag = imagem.getTag();
            if (!(tag instanceof Long) || !produtoId.equals(tag)) {
                return;
            }
            carregarUrl(appContext, imagem, url == null || url.trim().isEmpty()
                    ? urlLegada : url, placeholder);
        });
    }

    private static void carregarUrl(
            Context context,
            ImageView imagem,
            @Nullable String url,
            @DrawableRes int placeholder
    ) {
        String urlParaImagem = urlParaExibicao(url, 320);
        ImageLoader carregador = Coil.imageLoader(context);
        carregador.enqueue(new ImageRequest.Builder(context)
                .data(urlParaImagem)
                .target(imagem)
                .placeholder(placeholder)
                .error(placeholder)
                .fallback(placeholder)
                .crossfade(false)
                .build());
    }

    /**
     * Os assets do Cloudinary costumam ser originais grandes. Para cards, uma
     * variante pequena e com c_fit reduz o tempo de rede e preserva a foto
     * inteira, sem o corte implícito de c_fill/centerCrop.
     */
    @Nullable
    public static String urlParaExibicao(@Nullable String url, int tamanhoMaximo) {
        if (url == null || url.trim().isEmpty()
                || !url.contains("res.cloudinary.com")
                || !url.contains("/upload/")
                || url.contains("/s--")) {
            return url;
        }

        int tamanho = Math.max(160, Math.min(tamanhoMaximo, 1200));

        int inicioTransformacoes = url.indexOf("/upload/") + "/upload/".length();
        int proximaBarra = url.indexOf('/', inicioTransformacoes);
        if (proximaBarra > inicioTransformacoes) {
            String primeiraParte = url.substring(inicioTransformacoes, proximaBarra);
            if (primeiraParte.contains("w_")
                    || primeiraParte.contains("h_")
                    || primeiraParte.contains("c_")) {
                return url;
            }
        }

        return url.substring(0, inicioTransformacoes)
                + "f_auto,q_auto,w_" + tamanho + ",h_" + tamanho + ",c_fit/"
                + url.substring(inicioTransformacoes);
    }
}
