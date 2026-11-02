package com.venussystem.venusmobile.repository;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.venussystem.venusmobile.model.ItemHistorico;
import com.venussystem.venusmobile.model.Produto;
import com.venussystem.venusmobile.repository.api.ClienteApi;
import com.venussystem.venusmobile.repository.api.VenusApi;
import com.venussystem.venusmobile.repository.api.dto.FatiaResponse;
import com.venussystem.venusmobile.repository.api.dto.ScanSessionResponse;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Response;

public class HistoricoRepository {

    private static final int TAMANHO_PAGINA = 20;
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    @VisibleForTesting
    interface Catalogo {
        @Nullable
        Produto porId(long produtoId);
    }

    private final UsuarioApiRepository usuarios;
    private final VenusApi api;
    private final Context app;
    private final Catalogo catalogo;
    private final Executor executor;
    @Nullable
    private final ScanDraftStore drafts;
    private final MutableLiveData<List<ItemHistorico>> historico = new MutableLiveData<>();

    private final Object trava = new Object();
    private List<ItemHistorico> scans = Collections.emptyList();

    public HistoricoRepository(@NonNull Context context) {
        Context app = context.getApplicationContext();
        ProdutoRepository produtos = new ProdutoRepository();
        this.api = ClienteApi.get();
        this.usuarios = new UsuarioApiRepository(app);
        this.app = app;
        this.catalogo = produtos::buscarNoCache;
        this.executor = EXECUTOR;
        this.drafts = new ScanDraftStore(app.getNoBackupFilesDir().toPath()
                .resolve("scan-submissions"));
    }

    @VisibleForTesting
    HistoricoRepository(
            @NonNull VenusApi api,
            @NonNull UsuarioApiRepository usuarios,
            @NonNull Context context,
            @NonNull Catalogo catalogo,
            @NonNull Executor executor
    ) {
        this.api = api;
        this.usuarios = usuarios;
        this.app = context.getApplicationContext();
        this.catalogo = catalogo;
        this.executor = executor;
        this.drafts = null;
    }

    public LiveData<List<ItemHistorico>> getHistorico() {
        return historico;
    }

    public void carregar() {
        executor.execute(() -> {
            String uid = usuarios.uidAtual();
            List<ItemHistorico> local = carregarSubmissoesLocais(uid);
            Log.d("VENUS_HISTORY", "LOCAL_COUNT=" + local.size());
            boolean temAlgo = publicar(local, false);

            Long userId = usuarios.obterIdSincrono();
            if (userId == null || userId <= 0) {
                if (!temAlgo) publicar(local, true);
                return;
            }

            try {
                Response<FatiaResponse<ScanSessionResponse>> response =
                        api.listarScansDoUsuario(userId, 0, TAMANHO_PAGINA, "createdAt,desc")
                                .execute();
                if (!response.isSuccessful() || response.body() == null) {
                    Log.w("VENUS_HISTORY", "REMOTE_FAILED http=" + response.code());
                    if (!temAlgo) publicar(local, true);
                    return;
                }
                List<ItemHistorico> remoto = mapear(response.body().content);
                Log.d("VENUS_HISTORY", "REMOTE_COUNT=" + remoto.size());
                publicar(unir(local, remoto), true);
            } catch (IOException | RuntimeException exception) {
                Log.w("VENUS_HISTORY", "REMOTE_ERROR type=" + exception.getClass().getSimpleName());
                if (!temAlgo) publicar(local, true);
            }
        });
    }

    public void recombinar() {
        executor.execute(() -> {
            synchronized (trava) {
                List<ItemHistorico> itens = combinar(scans);
                if (!itens.isEmpty()) historico.postValue(itens);
            }
        });
    }

    private boolean publicar(@NonNull List<ItemHistorico> novosScans, boolean mesmoVazio) {
        synchronized (trava) {
            scans = novosScans;
            List<ItemHistorico> itens = combinar(novosScans);
            if (mesmoVazio || !itens.isEmpty()) historico.postValue(itens);
            return !itens.isEmpty();
        }
    }

    @NonNull
    private List<ItemHistorico> carregarSubmissoesLocais(@Nullable String uid) {
        if (drafts == null || uid == null || uid.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            List<ScanDraftStore.SavedDraft> saved = drafts.submitted(uid);
            List<ScanSessionResponse> scans = new ArrayList<>();
            for (ScanDraftStore.SavedDraft draft : saved) {
                if (draft != null && draft.submission != null) {
                    scans.add(draft.submission);
                }
            }
            return ordenar(mapear(scans));
        } catch (IOException | RuntimeException ignored) {
            return Collections.emptyList();
        }
    }

    @NonNull
    private static List<ItemHistorico> unir(
            @NonNull List<ItemHistorico> local,
            @NonNull List<ItemHistorico> remoto
    ) {
        Map<String, ItemHistorico> porScan = new LinkedHashMap<>();
        for (ItemHistorico item : remoto) porScan.put(chave(item), item);
        for (ItemHistorico item : local) porScan.putIfAbsent(chave(item), item);
        return ordenar(new ArrayList<>(porScan.values()));
    }

    @NonNull
    private List<ItemHistorico> combinar(@NonNull List<ItemHistorico> scansDaConta) {
        List<ItemHistorico> itens = new ArrayList<>(scansDaConta);
        String uid = usuarios.uidAtual();
        if (uid != null && !uid.trim().isEmpty()) {
            List<AcessosRecentesRepository.Acesso> acessos =
                    new AcessosRecentesRepository(app, uid).listar();
            for (AcessosRecentesRepository.Acesso acesso : acessos) {
                Produto produto = catalogo.porId(acesso.produtoId);
                if (produto != null) {
                    acrescentar(itens, scansDaConta.size(), produto, acesso);
                }
            }
        }
        return ordenar(itens);
    }

    private static void acrescentar(
            @NonNull List<ItemHistorico> itens,
            int quantidadeDeScans,
            @NonNull Produto produto,
            @NonNull AcessosRecentesRepository.Acesso acesso
    ) {
        LocalDate data = dataDoMomento(acesso.momento);
        for (int i = 0; i < quantidadeDeScans; i++) {
            ItemHistorico scan = itens.get(i);
            Long idDoScan = scan.getProduto() == null ? null : scan.getProduto().getId();
            if (idDoScan == null || idDoScan != acesso.produtoId) {
                continue;
            }
            boolean acessoMaisRecente = data.isAfter(scan.getData())
                    || (data.equals(scan.getData()) && acesso.momento > scan.getMomento());
            if (acessoMaisRecente) {
                itens.set(i, new ItemHistorico(produto, data, scan.getStatus(),
                        scan.getScanId(), acesso.momento));
            }
            return;
        }
        itens.add(new ItemHistorico(produto, data, null, null, acesso.momento));
    }

    @NonNull
    private static LocalDate dataDoMomento(long momento) {
        return Instant.ofEpochMilli(momento).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private static String chave(ItemHistorico item) {
        if (item.getScanId() != null && !item.getScanId().trim().isEmpty()) {
            return "scan:" + item.getScanId();
        }
        Produto produto = item.getProduto();
        return "fallback:" + (produto == null ? "" : String.valueOf(produto.getName()))
                + "|" + item.getData() + "|" + item.getStatus();
    }

    @NonNull
    private static List<ItemHistorico> ordenar(@NonNull List<ItemHistorico> itens) {
        itens.sort(Comparator.comparing(ItemHistorico::getData)
                .thenComparingLong(ItemHistorico::getMomento).reversed());
        if (itens.size() <= TAMANHO_PAGINA) return itens;
        return new ArrayList<>(itens.subList(0, TAMANHO_PAGINA));
    }

    @NonNull
    @VisibleForTesting
    List<ItemHistorico> mapear(@Nullable List<ScanSessionResponse> scans) {
        List<ItemHistorico> resultado = new ArrayList<>();
        if (scans == null) {
            return resultado;
        }

        for (ScanSessionResponse scan : scans) {
            if (scan == null) {
                continue;
            }
            ScanSessionResponse.Extracted extraido = extracaoDaFrente(scan);
            String nome = primeiroPreenchido(
                    extraido == null ? null : extraido.productName,
                    extraido == null ? null : extraido.presentation,
                    "Produto escaneado");
            String marca = extraido == null ? "" : limpar(extraido.brand);
            String imagem = imagemDaFrente(scan);
            Long produtoId = produtoIdSincronizado(scan);
            Produto produto = new Produto(produtoId, nome, marca, null, imagem, null, null);
            resultado.add(new ItemHistorico(produto, dataDoScan(scan), limpar(scan.status),
                    primeiroPreenchido(scan.scanId, scan.id, null), momentoDoScan(scan)));
        }
        return resultado;
    }

    @Nullable
    private static ScanSessionResponse.Extracted extracaoDaFrente(ScanSessionResponse scan) {
        return scan.ocr == null || scan.ocr.front == null
                ? null : scan.ocr.front.extracted;
    }

    @Nullable
    private static String imagemDaFrente(ScanSessionResponse scan) {
        if (scan.images == null || scan.images.front == null) {
            return null;
        }
        return scan.images.front.secureUrl;
    }

    @Nullable
    private static Long produtoIdSincronizado(ScanSessionResponse scan) {
        if (scan.sync == null || scan.sync.productId == null || scan.sync.productId <= 0) {
            return null;
        }
        return scan.sync.productId;
    }

    @NonNull
    private static String textoDaData(ScanSessionResponse scan) {
        return primeiroPreenchido(scan.createdAt, scan.finishedAt, scan.startedAt, null);
    }

    @NonNull
    private static LocalDate dataDoScan(ScanSessionResponse scan) {
        String data = textoDaData(scan);
        if (!data.isEmpty()) {
            try {
                return OffsetDateTime.parse(data).toLocalDate();
            } catch (RuntimeException ignored) {
                try {
                    return LocalDateTime.parse(data).toLocalDate();
                } catch (RuntimeException ignoredSemFuso) {
                }
            }
        }
        return LocalDate.now();
    }

    private static long momentoDoScan(ScanSessionResponse scan) {
        String data = textoDaData(scan);
        if (!data.isEmpty()) {
            try {
                return OffsetDateTime.parse(data).toInstant().toEpochMilli();
            } catch (RuntimeException ignored) {
                try {
                    return LocalDateTime.parse(data).atZone(ZoneId.systemDefault())
                            .toInstant().toEpochMilli();
                } catch (RuntimeException ignoredSemFuso) {
                }
            }
        }
        return 0L;
    }

    @NonNull
    private static String primeiroPreenchido(String... valores) {
        if (valores != null) {
            for (String valor : valores) {
                String limpo = limpar(valor);
                if (!limpo.isEmpty()) {
                    return limpo;
                }
            }
        }
        return "";
    }

    @NonNull
    private static String limpar(@Nullable String valor) {
        return valor == null ? "" : valor.trim();
    }
}
