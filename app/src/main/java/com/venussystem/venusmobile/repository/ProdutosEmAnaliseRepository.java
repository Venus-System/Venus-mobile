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

public class ProdutosEmAnaliseRepository {

    public interface AoCarregar {
        void aoCarregar(@Nullable List<ProdutoEmAnalise> produtos);
    }

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

    public void buscar(AoCarregar aoCarregar) {
        REDE.execute(() -> {
            List<ProdutoEmAnalise> produtos = buscarAgora();
            principal.execute(() -> aoCarregar.aoCarregar(produtos));
        });
    }

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
                return Situacao.AGUARDANDO;
        }
    }

    @Nullable
    private static String texto(@Nullable String valor) {
        return valor == null || valor.trim().isEmpty() ? null : valor.trim();
    }

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
