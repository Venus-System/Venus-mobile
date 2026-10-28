package com.venussystem.venusmobile.repository;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.venussystem.venusmobile.model.Mensagem;
import com.venussystem.venusmobile.repository.banco.ConversaDao;
import com.venussystem.venusmobile.repository.banco.ConversaEntity;
import com.venussystem.venusmobile.repository.banco.MensagemEntity;
import com.venussystem.venusmobile.repository.banco.VenusDatabase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A conversa com a Venus guardada no aparelho, no banco SQLite (tabelas
 * conversa e mensagem do VenusDatabase), separada por conta.
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

    // O arquivo de preferencias onde a conversa ficava antes do banco. So e
    // lido uma vez, para trazer a conversa de quem atualizou o app.
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

    // Formato do arquivo antigo, so para a migracao.
    private static final class Gravada {
        String conversationId;
        List<MensagemGravada> mensagens;
    }

    private static final class MensagemGravada {
        int autor;
        String texto;
    }

    private final VenusDatabase banco;
    private final ConversaDao dao;
    private final String uid;

    public ConversaSalvaRepository(Context context) {
        this(context, DadosDaConta.uidAtual(context));
    }

    @VisibleForTesting
    public ConversaSalvaRepository(Context context, @Nullable String uid) {
        this.banco = VenusDatabase.get(context);
        this.dao = banco.conversaDao();
        this.uid = DadosDaConta.daConta(uid);
        trazerDoArquivoAntigo(context, uid);
    }

    public Conversa carregar() {
        ConversaEntity conversa = dao.conversa(uid);
        List<Mensagem> mensagens = new ArrayList<>();
        for (MensagemEntity m : dao.mensagens(uid)) {
            mensagens.add(m.autor == Mensagem.DO_USUARIO
                    ? Mensagem.doUsuario(m.texto)
                    : Mensagem.doAssistente(m.texto));
        }
        return new Conversa(conversa == null ? null : conversa.conversationId, mensagens);
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
        return banco.runInTransaction(() -> {
            ConversaEntity conversa = dao.conversa(uid);
            String idGuardado = conversa == null ? null : conversa.conversationId;
            if (!Objects.equals(idGuardado, idEnviado)) {
                return false;
            }
            dao.inserirMensagens(Arrays.asList(
                    new MensagemEntity(uid, Mensagem.DO_USUARIO, pergunta),
                    new MensagemEntity(uid, Mensagem.DO_ASSISTENTE, resposta)));
            dao.manterSoAsUltimas(uid, LIMITE_MENSAGENS);
            dao.gravarConversa(new ConversaEntity(uid, idUsado));
            return true;
        });
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
        String id = UUID.randomUUID().toString();
        banco.runInTransaction(() -> {
            dao.apagarMensagens(uid);
            dao.gravarConversa(new ConversaEntity(uid, id));
        });
        return id;
    }

    public void apagar() {
        // Sincrono: quem chama sai da conta logo em seguida.
        banco.runInTransaction(() -> {
            dao.apagarMensagens(uid);
            dao.apagarConversa(uid);
        });
    }

    /**
     * Traz para o banco a conversa que estava no arquivo de preferencias, de
     * antes do banco existir, e apaga o arquivo. Depois da primeira vez nao
     * acha mais nada.
     */
    private void trazerDoArquivoAntigo(Context context, @Nullable String uidDaConta) {
        SharedPreferences antigo = DadosDaConta.prefs(context, ARQUIVO, uidDaConta);
        String json = antigo.getString(CHAVE, null);
        if (json == null) {
            return;
        }
        Gravada gravada = null;
        try {
            gravada = new Gson().fromJson(json, Gravada.class);
        } catch (JsonParseException e) {
            // Arquivo estragado nao pode travar o chat: fica so o banco.
        }
        if (gravada != null && dao.conversa(uid) == null) {
            List<MensagemEntity> mensagens = new ArrayList<>();
            if (gravada.mensagens != null) {
                for (MensagemGravada m : gravada.mensagens) {
                    if (m != null && m.texto != null) {
                        mensagens.add(new MensagemEntity(uid, m.autor, m.texto));
                    }
                }
            }
            ConversaEntity conversa = new ConversaEntity(uid, gravada.conversationId);
            banco.runInTransaction(() -> {
                dao.inserirMensagens(mensagens);
                dao.gravarConversa(conversa);
            });
        }
        antigo.edit().clear().commit();
        context.getApplicationContext()
                .deleteSharedPreferences(DadosDaConta.nomeDoArquivo(ARQUIVO, uidDaConta));
    }
}
