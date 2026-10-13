package com.venussystem.venusmobile.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.venussystem.venusmobile.repository.api.ClienteIa;
import com.venussystem.venusmobile.repository.api.VenusIaApi;
import com.venussystem.venusmobile.repository.api.dto.ChatRequest;
import com.venussystem.venusmobile.repository.api.dto.ChatResponse;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import retrofit2.Response;

/**
 * Conversa com a Venus pela Venus-AI-api (POST /v1/chat).
 *
 * A API identifica a pessoa pelo token do Firebase, nunca pelo corpo, e guarda
 * o historico da conversa do lado dela - o app so manda a mensagem nova e o id
 * da conversa.
 */
public class ConversaRepository {

    public enum Falha {
        /** Nenhuma URL da API de IA foi configurada neste build. */
        NAO_CONFIGURADO,
        SEM_CONEXAO,
        DEMOROU,
        SESSAO_EXPIRADA,
        FALHA_SERVIDOR
    }

    public interface AoResponder {
        void aoResponder(String resposta);

        void aoFalhar(Falha falha);
    }

    // Uma mensagem por vez: a API nao trava a conversa, entao duas perguntas em
    // paralelo podiam chegar fora de ordem no historico.
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler PRINCIPAL = new Handler(Looper.getMainLooper());

    private static final String PREFIXO_BEARER = "Bearer ";
    private static final int NAO_AUTORIZADO = 401;

    @Nullable
    private final VenusIaApi api;
    private final SessaoUsuario sessao;
    private final Supplier<Long> idUsuario;

    public ConversaRepository(Context context) {
        this(ClienteIa.get(), new SessaoFirebase(), new UsuarioApiRepository(context)::idEmCache);
    }

    /**
     * @param idUsuario id da pessoa no Venus-CRUD, ou null se ainda nao se sabe.
     */
    @VisibleForTesting
    public ConversaRepository(@Nullable VenusIaApi api, SessaoUsuario sessao,
                              Supplier<Long> idUsuario) {
        this.api = api;
        this.sessao = sessao;
        this.idUsuario = idUsuario;
    }

    /**
     * @param conversationId o mesmo em todas as mensagens de uma conversa; a
     *                       API guarda o historico por ele.
     * @param callback       sempre chamado na thread principal.
     */
    public void enviar(String mensagem, String conversationId, AoResponder callback) {
        if (api == null) {
            PRINCIPAL.post(() -> callback.aoFalhar(Falha.NAO_CONFIGURADO));
            return;
        }

        EXECUTOR.execute(() -> {
            Resultado resultado = conversar(api, mensagem, conversationId);
            PRINCIPAL.post(() -> {
                if (resultado.resposta != null) {
                    callback.aoResponder(resultado.resposta);
                } else {
                    callback.aoFalhar(resultado.falha);
                }
            });
        });
    }

    @WorkerThread
    private Resultado conversar(VenusIaApi api, String mensagem, String conversationId) {
        ChatRequest corpo = new ChatRequest();
        corpo.mensagem = mensagem;
        corpo.conversationId = conversationId;
        corpo.usuarioIdPostgres = idUsuario.get();

        try {
            String token = sessao.token(false);
            if (token == null) {
                return Resultado.falha(Falha.SESSAO_EXPIRADA);
            }
            Response<ChatResponse> resposta = api.conversar(PREFIXO_BEARER + token, corpo).execute();

            // O Firebase so troca o token perto de vencer. Se a API recusou, pede
            // um novo e tenta uma vez - repetir no 401 e seguro porque a API
            // recusou antes de a mensagem entrar na conversa.
            if (resposta.code() == NAO_AUTORIZADO) {
                token = sessao.token(true);
                if (token == null) {
                    return Resultado.falha(Falha.SESSAO_EXPIRADA);
                }
                resposta = api.conversar(PREFIXO_BEARER + token, corpo).execute();
            }

            if (resposta.code() == NAO_AUTORIZADO) {
                return Resultado.falha(Falha.SESSAO_EXPIRADA);
            }
            ChatResponse corpoResposta = resposta.body();
            if (!resposta.isSuccessful() || corpoResposta == null
                    || corpoResposta.resposta == null || corpoResposta.resposta.trim().isEmpty()) {
                return Resultado.falha(Falha.FALHA_SERVIDOR);
            }
            return Resultado.sucesso(corpoResposta.resposta.trim());
        } catch (SocketTimeoutException e) {
            // Estourar o tempo NAO e repetido: a API pode ter recebido a mensagem
            // e ainda estar respondendo, e a pergunta entraria duas vezes.
            return Resultado.falha(Falha.DEMOROU);
        } catch (IOException e) {
            return Resultado.falha(Falha.SEM_CONEXAO);
        }
    }

    private static class Resultado {
        @Nullable
        final String resposta;
        @Nullable
        final Falha falha;

        private Resultado(@Nullable String resposta, @Nullable Falha falha) {
            this.resposta = resposta;
            this.falha = falha;
        }

        static Resultado sucesso(String resposta) {
            return new Resultado(resposta, null);
        }

        static Resultado falha(Falha falha) {
            return new Resultado(null, falha);
        }
    }
}
