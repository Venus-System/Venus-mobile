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

public class ConversaSalvaRepository {

    static final String ARQUIVO = "venus_conversa";
    private static final String CHAVE = "conversa";

    @VisibleForTesting
    static final int LIMITE_MENSAGENS = 100;

    public static final class Conversa {
        @Nullable
        public final String conversationId;
        public final List<Mensagem> mensagens;

        Conversa(@Nullable String conversationId, List<Mensagem> mensagens) {
            this.conversationId = conversationId;
            this.mensagens = Collections.unmodifiableList(mensagens);
        }
    }

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

    public String novaConversa() {
        Gravada gravada = new Gravada();
        gravada.conversationId = UUID.randomUUID().toString();
        gravada.mensagens = new ArrayList<>();
        salvar(gravada);
        return gravada.conversationId;
    }

    public void apagar() {
        prefs.edit().clear().commit();
    }

    private Gravada ler() {
        Gravada gravada = null;
        String json = prefs.getString(CHAVE, null);
        if (json != null) {
            try {
                gravada = gson.fromJson(json, Gravada.class);
            } catch (JsonParseException e) {
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
