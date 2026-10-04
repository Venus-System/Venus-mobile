package com.venussystem.venusmobile.repository;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.core.content.ContextCompat;

import com.venussystem.venusmobile.model.ProdutoEmAnalise;
import com.venussystem.venusmobile.model.ProdutoEmAnalise.Situacao;
import com.venussystem.venusmobile.repository.api.ClienteApi;
import com.venussystem.venusmobile.repository.api.VenusApi;
import com.venussystem.venusmobile.repository.api.dto.FatiaResponse;
import com.venussystem.venusmobile.repository.api.dto.ScanEnviadoResponse;

import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Response;

/**
 * Os produtos que a pessoa escaneou e mandou para a equipe conferir (o envio
 * e do fluxo de scan). Sempre lidos da API, sem copia no aparelho: o status
 * muda do lado do servidor, quando alguem aprova ou recusa.
 */
public class ProdutosEmAnaliseRepository {

    public interface AoCarregar {
        /** @param produtos do mais recente para o mais antigo; null se nao deu para buscar. */
        void aoCarregar(@Nullable List<ProdutoEmAnalise> produtos);
    }

    // Uma pagina so: e mais do que alguem escaneia de produto novo, e a API ja
    // manda os mais recentes primeiro.
    private static final int TAMANHO_PAGINA = 50;

    private static final int PROIBIDO = 403;
    private static final int NAO_ENCONTRADO = 404;

    private static final ExecutorService REDE = Executors.newSingleThreadExecutor();

    private final UsuarioApiRepository usuarios;
    private final VenusApi api;
    private final Executor principal;

    public ProdutosEmAnaliseRepository(Context context) {
        this(new UsuarioApiRepository(context), ClienteApi.get(),
                ContextCompat.getMainExecutor(context));
    }

    @VisibleForTesting
    public ProdutosEmAnaliseRepository(UsuarioApiRepository usuarios, VenusApi api,
                                       Executor principal) {
        this.usuarios = usuarios;
        this.api = api;
        this.principal = principal;
    }

    /** Busca em segundo plano e avisa na thread principal. */
    public void buscar(AoCarregar aoCarregar) {
        REDE.execute(() -> {
            List<ProdutoEmAnalise> produtos = buscarAgora();
            principal.execute(() -> aoCarregar.aoCarregar(produtos));
        });
    }

    /**
     * @return a lista (vazia se a pessoa nunca mandou nada), ou null se nao deu
     * para descobrir quem e a pessoa na API ou a API respondeu erro.
     */
    @WorkerThread
    @Nullable
    public List<ProdutoEmAnalise> buscarAgora() {
        Long userId = usuarios.obterIdSincrono();
        if (userId == null) {
            return null;
        }
        try {
            Response<FatiaResponse<ScanEnviadoResponse>> resposta =
                    api.scansDoUsuario(userId, 0, TAMANHO_PAGINA).execute();
            if (!resposta.isSuccessful()) {
                // Id guardado de um banco que foi recriado: pode ser de outra
                // pessoa (403) ou de ninguem (404). Esquece para buscar de novo.
                if (resposta.code() == PROIBIDO || resposta.code() == NAO_ENCONTRADO) {
                    usuarios.esquecerId();
                }
                return null;
            }
            FatiaResponse<ScanEnviadoResponse> fatia = resposta.body();
            List<ProdutoEmAnalise> produtos = new ArrayList<>();
            if (fatia != null && fatia.content != null) {
                for (ScanEnviadoResponse scan : fatia.content) {
                    produtos.add(converter(scan));
                }
            }
            return produtos;
        } catch (IOException | RuntimeException e) {
            // Sem rede, ou um JSON que o Gson nao conseguiu ler.
            return null;
        }
    }

    private static ProdutoEmAnalise converter(ScanEnviadoResponse scan) {
        Situacao situacao = situacao(scan.status);
        ScanEnviadoResponse.Extraido frente = scan.ocr != null && scan.ocr.front != null
                ? scan.ocr.front.extracted : null;
        ScanEnviadoResponse.Imagens imagens = scan.images;
        ScanEnviadoResponse.Decisao decisao = scan.review;

        return new ProdutoEmAnalise(
                scan.id,
                situacao,
                frente == null ? null : texto(frente.productName),
                frente == null ? null : texto(frente.brand),
                imagens == null || imagens.front == null ? null : imagens.front.secureUrl,
                imagens == null || imagens.back == null ? null : imagens.back.secureUrl,
                dia(scan.createdAt),
                decisao == null ? null : dia(decisao.decidedAt),
                decisao == null ? null : texto(decisao.reason),
                // Antes do SYNCED o produto pode nao estar no catalogo ainda.
                "SYNCED".equals(scan.status) && scan.sync != null ? scan.sync.productId : null);
    }

    private static Situacao situacao(@Nullable String status) {
        if (status == null) {
            return Situacao.AGUARDANDO;
        }
        switch (status) {
            case "IN_REVIEW":
                return Situacao.EM_ANALISE;
            case "APPROVED":
            case "SYNCED":
            case "SYNC_FAILED":
                return Situacao.APROVADO;
            case "REJECTED":
                return Situacao.RECUSADO;
            default:
                // PENDING_REVIEW, ou um status novo que o app ainda nao conhece:
                // foi enviado e ninguem decidiu.
                return Situacao.AGUARDANDO;
        }
    }

    @Nullable
    private static String texto(@Nullable String valor) {
        return valor == null || valor.trim().isEmpty() ? null : valor.trim();
    }

    /**
     * O dia no fuso do aparelho: um envio as 23h30 em Sao Paulo ja e dia
     * seguinte em UTC, mas para a pessoa foi "hoje".
     */
    @Nullable
    private static LocalDate dia(@Nullable String dataHora) {
        if (dataHora == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(dataHora)
                    .atZoneSameInstant(ZoneId.systemDefault())
                    .toLocalDate();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
