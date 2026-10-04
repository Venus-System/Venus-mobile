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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Response;

/** Carrega os scans recentes da conta logada para a tela inicial. */
public class HistoricoRepository {

    private static final int TAMANHO_PAGINA = 20;
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    private final UsuarioApiRepository usuarios;
    private final VenusApi api;
    @Nullable
    private final ScanDraftStore drafts;
    private final MutableLiveData<List<ItemHistorico>> historico = new MutableLiveData<>();

    public HistoricoRepository(@NonNull Context context) {
        Context app = context.getApplicationContext();
        this.api = ClienteApi.get();
        this.usuarios = new UsuarioApiRepository(app);
        this.drafts = new ScanDraftStore(app.getNoBackupFilesDir().toPath()
                .resolve("scan-submissions"));
    }

    @VisibleForTesting
    HistoricoRepository(
            @NonNull VenusApi api,
            @NonNull UsuarioApiRepository usuarios
    ) {
        this.api = api;
        this.usuarios = usuarios;
        this.drafts = null;
    }

    public LiveData<List<ItemHistorico>> getHistorico() {
        return historico;
    }

    /** Busca apenas os scans da conta atual, nunca o histórico de outra conta. */
    public void carregar() {
        EXECUTOR.execute(() -> {
            String uid = usuarios.uidAtual();
            List<ItemHistorico> local = carregarSubmissoesLocais(uid);
            Log.d("VENUS_HISTORY", "LOCAL_COUNT=" + local.size());
            if (!local.isEmpty()) {
                historico.postValue(local);
            }

            Long userId = usuarios.obterIdSincrono();
            if (userId == null || userId <= 0) {
                if (local.isEmpty()) historico.postValue(Collections.emptyList());
                return;
            }

            try {
                Response<FatiaResponse<ScanSessionResponse>> response =
                        api.listarScansDoUsuario(userId, 0, TAMANHO_PAGINA, "createdAt,desc")
                                .execute();
                if (!response.isSuccessful() || response.body() == null) {
                    Log.w("VENUS_HISTORY", "REMOTE_FAILED http=" + response.code());
                    if (local.isEmpty()) historico.postValue(Collections.emptyList());
                    return;
                }
                List<ItemHistorico> remoto = mapear(response.body().content);
                Log.d("VENUS_HISTORY", "REMOTE_COUNT=" + remoto.size());
                historico.postValue(unir(local, remoto));
            } catch (IOException | RuntimeException exception) {
                // A home continua utilizável mesmo quando a API está dormindo ou offline.
                Log.w("VENUS_HISTORY", "REMOTE_ERROR type=" + exception.getClass().getSimpleName());
                if (local.isEmpty()) historico.postValue(Collections.emptyList());
            }
        });
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

    /** Mantém o cache local quando a API ainda não indexou o scan. */
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
        itens.sort(Comparator.comparing(ItemHistorico::getData).reversed());
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
                    primeiroPreenchido(scan.scanId, scan.id, null)));
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
    private static LocalDate dataDoScan(ScanSessionResponse scan) {
        String data = primeiroPreenchido(scan.createdAt, scan.finishedAt, scan.startedAt, null);
        if (!data.isEmpty()) {
            try {
                return OffsetDateTime.parse(data).toLocalDate();
            } catch (RuntimeException ignored) {
                try {
                    return LocalDateTime.parse(data).toLocalDate();
                } catch (RuntimeException ignoredSemFuso) {
                    // Resposta antiga ou fora do formato: o item ainda pode aparecer na home.
                }
            }
        }
        return LocalDate.now();
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
