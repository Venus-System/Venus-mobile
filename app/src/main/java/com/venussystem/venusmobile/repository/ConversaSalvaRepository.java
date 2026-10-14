package com.venussystem.venusmobile.repository;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.venussystem.venusmobile.model.Mensagem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A conversa com a Venus guardada no aparelho, separada por conta (ver
 * DadosDaConta).
 *
 * A Venus-AI-api guarda o historico do lado dela, mas nao tem rota para
 * devolve-lo. Sem esta copia, reabrir o chat mostraria uma tela vazia enquanto
 * a Venus ainda lembra de tudo. So entram perguntas que tiveram resposta: o
 * "Digitando..." e os avisos de erro sao da tela, nao da conversa.
 *
 * Sair da conta apaga (AutenticacaoRepository.sair), porque a conversa costuma
 * ter pele, alergias e saude da pessoa.
 */
public class ConversaSalvaRepository {

    static final String ARQUIVO = "venus_conversa";
    private static final String CHAVE = "conversa";

    // As mais antigas saem primeiro. A Venus nao perde nada com isso: o
    // historico completo continua com ela.
    @VisibleForTesting
    static final int LIMITE_MENSAGENS = 100;

    /** O que a tela precisa para retomar a conversa. */
    public static final class Conversa {
        /** Null antes da primeira resposta: a API escolhe o id. */
        @Nullable
        public final String conversationId;
        public final List<Mensagem> mensagens;

        Conversa(@Nullable String conversationId, List<Mensagem> mensagens) {
            this.conversationId = conversationId;
            this.mensagens = Collections.unmodifiableList(mensagens);
        }
    }

    // Formato gravado. Separado do Mensagem para mudar o modelo da tela sem
    // perder o que ja esta salvo nos aparelhos.
    private static final class Gravada {
        String conversationId;
        List<MensagemGravada> mensagens;
    }

    private static final class MensagemGravada {
        int autor;
        String texto;
    }

    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    public ConversaSalvaRepository(Context context) {
        this(context, DadosDaConta.uidAtual(context));
    }

    @VisibleForTesting
    public ConversaSalvaRepository(Context context, @Nullable String uid) {
        this.prefs = DadosDaConta.prefs(context, ARQUIVO, uid);
    }

    public Conversa carregar() {
        Gravada gravada = ler();
        List<Mensagem> mensagens = new ArrayList<>();
        for (MensagemGravada m : gravada.mensagens) {
            if (m == null || m.texto == null) {
                continue;
            }
            mensagens.add(m.autor == Mensagem.DO_USUARIO
                    ? Mensagem.doUsuario(m.texto)
                    : Mensagem.doAssistente(m.texto));
        }
        return new Conversa(gravada.conversationId, mensagens);
    }

    /**
     * Guarda uma pergunta e a resposta dela.
     *
     * @param idEnviado o id com que a pergunta foi mandada. Se a conversa
     *                  guardada ja e outra (a pessoa comecou uma nova enquanto
     *                  a resposta vinha), o par e descartado em vez de cair na
     *                  conversa errada.
     * @param idUsado   o id que a API devolveu; vale para as proximas.
     * @return false quando o par foi descartado.
     */
    public boolean adicionarPar(@Nullable String idEnviado, String pergunta, String resposta,
                                @Nullable String idUsado) {
        Gravada gravada = ler();
        if (!Objects.equals(gravada.conversationId, idEnviado)) {
            return false;
        }
        gravada.mensagens.add(gravar(Mensagem.DO_USUARIO, pergunta));
        gravada.mensagens.add(gravar(Mensagem.DO_ASSISTENTE, resposta));
        while (gravada.mensagens.size() > LIMITE_MENSAGENS) {
            gravada.mensagens.remove(0);
        }
        gravada.conversationId = idUsado;
        salvar(gravada);
        return true;
    }

    /**
     * Esquece as mensagens e comeca outra conversa na API.
     *
     * O id novo ja fica gravado: sem id, a API cairia de volta na conversa
     * pelo UID, que e justamente a antiga.
     *
     * @return o id da conversa nova.
     */
    public String novaConversa() {
        Gravada gravada = new Gravada();
        gravada.conversationId = UUID.randomUUID().toString();
        gravada.mensagens = new ArrayList<>();
        salvar(gravada);
        return gravada.conversationId;
    }

    public void apagar() {
        // commit, e nao apply: quem chama sai da conta logo em seguida.
        prefs.edit().clear().commit();
    }

    private Gravada ler() {
        Gravada gravada = null;
        String json = prefs.getString(CHAVE, null);
        if (json != null) {
            try {
                gravada = gson.fromJson(json, Gravada.class);
            } catch (JsonParseException e) {
                // Arquivo estragado nao pode travar o chat: comeca do zero.
                gravada = null;
            }
        }
        if (gravada == null) {
            gravada = new Gravada();
        }
        if (gravada.mensagens == null) {
            gravada.mensagens = new ArrayList<>();
        }
        return gravada;
    }

    private void salvar(Gravada gravada) {
        prefs.edit().putString(CHAVE, gson.toJson(gravada)).apply();
    }

    private static MensagemGravada gravar(int autor, String texto) {
        MensagemGravada m = new MensagemGravada();
        m.autor = autor;
        m.texto = texto;
        return m;
    }
}
