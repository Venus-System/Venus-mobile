package com.venussystem.venusmobile.repository;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
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

import retrofit2.Call;
import retrofit2.Callback;
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
        /** 429: passou do limite de mensagens por minuto ou por dia da API. */
        MUITAS_MENSAGENS,
        /** 422: a API recusou a mensagem (vazia ou longa demais). */
        MENSAGEM_INVALIDA,
        FALHA_SERVIDOR
    }

    public interface AoResponder {
        /**
         * @param conversationId o id que a API usou nesta conversa; e ele que
         *                       vai nas proximas mensagens.
         * @param expressao      a cara da Veninha que a API mandou com a
         *                       resposta ("neutra" ou "magoada"); null se nao veio.
         */
        void aoResponder(String resposta, @Nullable String conversationId,
                         @Nullable String expressao);

        void aoFalhar(Falha falha);
    }

    // Uma mensagem por vez: a API nao trava a conversa, entao duas perguntas em
    // paralelo podiam chegar fora de ordem no historico.
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler PRINCIPAL = new Handler(Looper.getMainLooper());

    private static final String PREFIXO_BEARER = "Bearer ";
    private static final int NAO_AUTORIZADO = 401;
    private static final int NAO_PROCESSAVEL = 422;
    private static final int MUITOS_PEDIDOS = 429;

    @Nullable
    private final VenusIaApi api;
    private final SessaoUsuario sessao;

    public ConversaRepository() {
        this(ClienteIa.get(), new SessaoFirebase());
    }

    @VisibleForTesting
    public ConversaRepository(@Nullable VenusIaApi api, SessaoUsuario sessao) {
        this.api = api;
        this.sessao = sessao;
    }

    /**
     * Chama a rota de health da IA sem esperar resposta. No Render, a primeira
     * mensagem depois de o servidor dormir levava mais de um minuto, e disparar
     * isto ao abrir a tela fazia esse tempo correr enquanto a pessoa escrevia.
     * Na AWS ele nao dorme: a chamada so adianta a conexao.
     */
    public void acordar() {
        if (api == null) {
            return;
        }
        api.acordar().enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                // So interessava o servidor de pe.
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                // Sem rede aqui, a mensagem de verdade avisa quando for enviada.
            }
        });
    }

    /**
     * @param conversationId o mesmo em todas as mensagens de uma conversa; a
     *                       API guarda o historico por ele. Null na primeira:
     *                       a API escolhe e devolve no callback.
     * @param callback       sempre chamado na thread principal.
     */
    public void enviar(String mensagem, @Nullable String conversationId, AoResponder callback) {
        if (api == null) {
            PRINCIPAL.post(() -> callback.aoFalhar(Falha.NAO_CONFIGURADO));
            return;
        }

        EXECUTOR.execute(() -> {
            Resultado resultado = conversar(api, mensagem, conversationId);
            PRINCIPAL.post(() -> {
                if (resultado.resposta != null) {
                    callback.aoResponder(resultado.resposta, resultado.conversationId,
                            resultado.expressao);
                } else {
                    callback.aoFalhar(resultado.falha);
                }
            });
        });
    }

    @WorkerThread
    private Resultado conversar(VenusIaApi api, String mensagem, @Nullable String conversationId) {
        ChatRequest corpo = new ChatRequest();
        corpo.mensagem = mensagem;
        corpo.conversationId = conversationId;

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

            switch (resposta.code()) {
                case NAO_AUTORIZADO:
                    return Resultado.falha(Falha.SESSAO_EXPIRADA);
                case MUITOS_PEDIDOS:
                    return Resultado.falha(Falha.MUITAS_MENSAGENS);
                case NAO_PROCESSAVEL:
                    return Resultado.falha(Falha.MENSAGEM_INVALIDA);
                default:
                    break;
            }
            ChatResponse corpoResposta = resposta.body();
            if (!resposta.isSuccessful() || corpoResposta == null
                    || corpoResposta.resposta == null || corpoResposta.resposta.trim().isEmpty()) {
                return Resultado.falha(Falha.FALHA_SERVIDOR);
            }
            // A API sempre devolve o id; se um dia vier vazio, segue o enviado.
            String idUsado = corpoResposta.conversationId == null
                    || corpoResposta.conversationId.trim().isEmpty()
                    ? conversationId
                    : corpoResposta.conversationId;
            return Resultado.sucesso(corpoResposta.resposta.trim(), idUsado, corpoResposta.expressao);
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
        final String conversationId;
        @Nullable
        final String expressao;
        @Nullable
        final Falha falha;

        private Resultado(@Nullable String resposta, @Nullable String conversationId,
                          @Nullable String expressao, @Nullable Falha falha) {
            this.resposta = resposta;
            this.conversationId = conversationId;
            this.expressao = expressao;
            this.falha = falha;
        }

        static Resultado sucesso(String resposta, @Nullable String conversationId,
                                 @Nullable String expressao) {
            return new Resultado(resposta, conversationId, expressao, null);
        }

        static Resultado falha(Falha falha) {
            return new Resultado(null, null, null, falha);
        }
    }
}
