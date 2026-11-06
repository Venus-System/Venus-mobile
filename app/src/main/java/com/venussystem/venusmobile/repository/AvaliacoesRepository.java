package com.venussystem.venusmobile.repository;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.core.content.ContextCompat;

import com.venussystem.venusmobile.model.Avaliacao;
import com.venussystem.venusmobile.repository.api.ClienteApi;
import com.venussystem.venusmobile.repository.api.VenusApi;
import com.venussystem.venusmobile.repository.api.dto.FatiaResponse;
import com.venussystem.venusmobile.repository.api.dto.ProductVersionResponse;
import com.venussystem.venusmobile.repository.api.dto.ReportRequest;
import com.venussystem.venusmobile.repository.api.dto.ReviewRequest;
import com.venussystem.venusmobile.repository.api.dto.ReviewResponse;
import com.venussystem.venusmobile.repository.api.dto.ReviewVoteRequest;
import com.venussystem.venusmobile.repository.api.dto.ReviewVoteResponse;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Response;

public class AvaliacoesRepository {

    public static final class Lidas {
        private final List<Avaliacao> avaliacoes;
        private final boolean truncada;

        Lidas(List<Avaliacao> avaliacoes, boolean truncada) {
            this.avaliacoes = Collections.unmodifiableList(avaliacoes);
            this.truncada = truncada;
        }

        public List<Avaliacao> getAvaliacoes() {
            return avaliacoes;
        }

        public boolean isTruncada() {
            return truncada;
        }
    }

    public enum Envio {
        OK, DUPLICADA, SEM_CONTA, SEM_VERSAO, FALHA
    }

    public interface AoCarregar {
        void aoCarregar(@Nullable Lidas lidas);
    }

    public interface AoEnviar {
        void aoEnviar(Envio resultado);
    }

    public interface AoLerVoto {
        void aoLer(boolean leu, @Nullable Avaliacao.Voto voto);
    }

    private static final int TAMANHO_PAGINA = 50;
    private static final int LIMITE_DE_PAGINAS = 4;
    private static final String ORDEM = "createdAt,desc";

    private static final String VOTO_UTIL = "USEFUL";
    private static final String VOTO_NAO_UTIL = "NOT_USEFUL";
    private static final String ALVO_AVALIACAO = "REVIEW";
    private static final String DENUNCIA_ABERTA = "OPEN";

    private static final int PROIBIDO = 403;
    private static final int NAO_ENCONTRADO = 404;
    private static final int CONFLITO = 409;

    private static final ExecutorService REDE = Executors.newFixedThreadPool(3);
    private static final Map<Long, Long> VERSOES = new ConcurrentHashMap<>();

    private final UsuarioApiRepository usuarios;
    private final VenusApi api;
    private final Executor principal;
    private final Executor rede;

    public AvaliacoesRepository(Context context) {
        this(new UsuarioApiRepository(context), ClienteApi.get(),
                ContextCompat.getMainExecutor(context), REDE);
    }

    @VisibleForTesting
    public AvaliacoesRepository(UsuarioApiRepository usuarios, VenusApi api,
                                Executor principal, Executor rede) {
        this.usuarios = usuarios;
        this.api = api;
        this.principal = principal;
        this.rede = rede;
    }

    @VisibleForTesting
    public static void esquecerVersoes() {
        VERSOES.clear();
    }

    public void buscar(long produtoId, AoCarregar aoCarregar) {
        rede.execute(() -> {
            Lidas lidas = buscarAgora(produtoId);
            principal.execute(() -> aoCarregar.aoCarregar(lidas));
        });
    }

    public void publicar(long produtoId, int nota, String titulo, String comentario,
                         boolean usou, AoEnviar aoEnviar) {
        rede.execute(() -> {
            Envio resultado = publicarAgora(produtoId, nota, titulo, comentario, usou);
            principal.execute(() -> aoEnviar.aoEnviar(resultado));
        });
    }

    public void meuVoto(long avaliacaoId, AoLerVoto aoLer) {
        rede.execute(() -> {
            Long userId = usuarios.obterIdSincrono();
            if (userId == null) {
                principal.execute(() -> aoLer.aoLer(false, null));
                return;
            }
            boolean leu = false;
            Avaliacao.Voto voto = null;
            try {
                Response<ReviewVoteResponse> resposta =
                        api.meuVoto(avaliacaoId, userId).execute();
                if (resposta.isSuccessful()) {
                    leu = true;
                    voto = resposta.body() == null ? null : voto(resposta.body().voteType);
                } else if (resposta.code() == NAO_ENCONTRADO) {
                    leu = true;
                } else if (resposta.code() == PROIBIDO) {
                    usuarios.esquecerId();
                }
            } catch (IOException | RuntimeException e) {
                leu = false;
            }
            boolean lido = leu;
            Avaliacao.Voto lidoVoto = voto;
            principal.execute(() -> aoLer.aoLer(lido, lidoVoto));
        });
    }

    public void votar(long avaliacaoId, @Nullable Avaliacao.Voto novo,
                      @Nullable Avaliacao.Voto anterior, AoEnviar aoEnviar) {
        rede.execute(() -> {
            Envio resultado = votarAgora(avaliacaoId, novo, anterior);
            principal.execute(() -> aoEnviar.aoEnviar(resultado));
        });
    }

    public void denunciar(long avaliacaoId, String motivo, AoEnviar aoEnviar) {
        rede.execute(() -> {
            Envio resultado = denunciarAgora(avaliacaoId, motivo);
            principal.execute(() -> aoEnviar.aoEnviar(resultado));
        });
    }

    @WorkerThread
    @Nullable
    Lidas buscarAgora(long produtoId) {
        Long versaoId = versao(produtoId);
        if (versaoId == null) {
            return null;
        }
        Long userId = usuarios.obterIdSincrono();
        List<Avaliacao> avaliacoes = new ArrayList<>();
        Set<Long> idsLidos = new HashSet<>();
        try {
            for (int pagina = 0; pagina < LIMITE_DE_PAGINAS; pagina++) {
                Response<FatiaResponse<ReviewResponse>> resposta = api.avaliacoesDaVersao(
                        versaoId, pagina, TAMANHO_PAGINA, ORDEM).execute();
                if (!resposta.isSuccessful()) {
                    return null;
                }
                FatiaResponse<ReviewResponse> fatia = resposta.body();
                List<ReviewResponse> daPagina = fatia == null || fatia.content == null
                        ? Collections.emptyList() : fatia.content;
                for (ReviewResponse avaliacao : daPagina) {
                    Avaliacao convertida = converter(avaliacao, userId, versaoId);
                    if (convertida != null && idsLidos.add(convertida.getId())) {
                        avaliacoes.add(convertida);
                    }
                }
                if ((fatia != null && Boolean.TRUE.equals(fatia.last))
                        || daPagina.size() < TAMANHO_PAGINA) {
                    return new Lidas(avaliacoes, false);
                }
            }
            return new Lidas(avaliacoes, true);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    @WorkerThread
    Envio publicarAgora(long produtoId, int nota, String titulo, String comentario,
                        boolean usou) {
        if (nota < 1 || nota > 5 || titulo == null || comentario == null) {
            return Envio.FALHA;
        }
        Long userId = usuarios.obterIdSincrono();
        if (userId == null) {
            return Envio.SEM_CONTA;
        }
        Long versaoId = versao(produtoId);
        if (versaoId == null) {
            return Envio.SEM_VERSAO;
        }
        try {
            Response<ReviewResponse> resposta = api.publicarAvaliacao(new ReviewRequest(
                    userId, versaoId, nota, titulo.trim(), comentario.trim(), usou)).execute();
            if (resposta.isSuccessful()) {
                return Envio.OK;
            }
            return falha(resposta.code());
        } catch (IOException | RuntimeException e) {
            return Envio.FALHA;
        }
    }

    @WorkerThread
    Envio votarAgora(long avaliacaoId, @Nullable Avaliacao.Voto novo,
                     @Nullable Avaliacao.Voto anterior) {
        Long userId = usuarios.obterIdSincrono();
        if (userId == null) {
            return Envio.SEM_CONTA;
        }
        try {
            Response<Void> resposta;
            if (novo == null) {
                resposta = api.apagarVoto(avaliacaoId, userId).execute();
                if (resposta.code() == NAO_ENCONTRADO) {
                    return Envio.OK;
                }
            } else {
                ReviewVoteRequest voto = new ReviewVoteRequest(avaliacaoId, userId, tipo(novo));
                resposta = anterior == null
                        ? api.votar(voto).execute()
                        : api.trocarVoto(avaliacaoId, userId, voto).execute();
            }
            return resposta.isSuccessful() ? Envio.OK : falha(resposta.code());
        } catch (IOException | RuntimeException e) {
            return Envio.FALHA;
        }
    }

    @WorkerThread
    Envio denunciarAgora(long avaliacaoId, String motivo) {
        Long userId = usuarios.obterIdSincrono();
        if (userId == null) {
            return Envio.SEM_CONTA;
        }
        try {
            Response<Void> resposta = api.denunciar(new ReportRequest(
                    userId, ALVO_AVALIACAO, avaliacaoId, motivo, DENUNCIA_ABERTA)).execute();
            return resposta.isSuccessful() ? Envio.OK : falha(resposta.code());
        } catch (IOException | RuntimeException e) {
            return Envio.FALHA;
        }
    }

    private Envio falha(int codigo) {
        if (codigo == CONFLITO) {
            return Envio.DUPLICADA;
        }
        if (codigo == PROIBIDO) {
            usuarios.esquecerId();
        }
        return Envio.FALHA;
    }

    @Nullable
    private Long versao(long produtoId) {
        Long guardada = VERSOES.get(produtoId);
        if (guardada != null) {
            return guardada;
        }
        try {
            Response<ProductVersionResponse> resposta = api.versaoAtual(produtoId).execute();
            if (!resposta.isSuccessful() || resposta.body() == null
                    || resposta.body().id == null) {
                return null;
            }
            VERSOES.put(produtoId, resposta.body().id);
            return resposta.body().id;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    @Nullable
    private static Avaliacao converter(ReviewResponse avaliacao, @Nullable Long userId,
                                       long versaoId) {
        if (avaliacao == null || avaliacao.id == null || avaliacao.rating == null
                || avaliacao.rating.isNaN() || avaliacao.rating.isInfinite()
                || avaliacao.rating < 1d || avaliacao.rating > 5d
                || (avaliacao.productVersionId != null
                && avaliacao.productVersionId != versaoId)) {
            return null;
        }
        long votosUteis = avaliacao.usefulVotes == null
                ? 0L : Math.max(0L, avaliacao.usefulVotes);
        return new Avaliacao(
                avaliacao.id,
                texto(avaliacao.authorName),
                userId != null && userId.equals(avaliacao.userId),
                avaliacao.rating,
                avaliacao.title == null ? "" : avaliacao.title.trim(),
                avaliacao.comment == null ? "" : avaliacao.comment.trim(),
                Boolean.TRUE.equals(avaliacao.verifiedUse),
                votosUteis,
                dataHora(avaliacao.createdAt));
    }

    @Nullable
    private static Avaliacao.Voto voto(@Nullable String tipo) {
        if (VOTO_UTIL.equals(tipo)) {
            return Avaliacao.Voto.UTIL;
        }
        return VOTO_NAO_UTIL.equals(tipo) ? Avaliacao.Voto.NAO_UTIL : null;
    }

    private static String tipo(Avaliacao.Voto voto) {
        return voto == Avaliacao.Voto.UTIL ? VOTO_UTIL : VOTO_NAO_UTIL;
    }

    @Nullable
    private static String texto(@Nullable String valor) {
        return valor == null || valor.trim().isEmpty() ? null : valor.trim();
    }

    @Nullable
    private static OffsetDateTime dataHora(@Nullable String valor) {
        if (valor == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(valor);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
