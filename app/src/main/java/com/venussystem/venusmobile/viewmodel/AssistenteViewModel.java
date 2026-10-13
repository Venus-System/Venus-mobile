package com.venussystem.venusmobile.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.Mensagem;
import com.venussystem.venusmobile.repository.ConversaRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Estado da conversa com a Venus. Fica no ViewModel, e nao na Activity, porque
 * uma resposta pode levar mais de um minuto: girar a tela no meio disso nao
 * pode apagar a conversa nem perder a resposta que esta chegando.
 */
public class AssistenteViewModel extends AndroidViewModel {

    private final ConversaRepository conversa;

    // Um id novo por conversa aberta. A API nao tem rota para trazer o historico
    // de volta, entao abrir a tela vazia e comecar uma conversa nova combinam; o
    // que a Venus sabe da pessoa (nome, pele, alergias) vale entre conversas
    // mesmo assim, porque ela guarda pelo UID.
    private final String conversationId = UUID.randomUUID().toString();

    private final List<Mensagem> mensagens = new ArrayList<>();
    private final MutableLiveData<List<Mensagem>> mensagensVisiveis = new MutableLiveData<>();
    private final MutableLiveData<Boolean> aguardando = new MutableLiveData<>(false);

    public AssistenteViewModel(@NonNull Application application) {
        this(application, new ConversaRepository(application));
    }

    @VisibleForTesting
    AssistenteViewModel(@NonNull Application application, ConversaRepository conversa) {
        super(application);
        this.conversa = conversa;
        adicionar(Mensagem.doAssistente(application.getString(R.string.assistente_saudacao)));
    }

    public LiveData<List<Mensagem>> getMensagens() {
        return mensagensVisiveis;
    }

    public LiveData<Boolean> getAguardando() {
        return aguardando;
    }

    /**
     * @return false quando a pergunta foi ignorada (vazia, ou a Venus ainda esta
     * respondendo a anterior) - a tela usa isso para nao apagar o que foi digitado.
     */
    public boolean perguntar(String texto) {
        String pergunta = texto == null ? "" : texto.trim();
        if (pergunta.isEmpty() || Boolean.TRUE.equals(aguardando.getValue())) {
            return false;
        }

        adicionar(Mensagem.doUsuario(pergunta));
        adicionar(Mensagem.doAssistente(getApplication().getString(R.string.assistente_digitando)));
        aguardando.setValue(true);

        conversa.enviar(pergunta, conversationId, new ConversaRepository.AoResponder() {
            @Override
            public void aoResponder(String resposta) {
                trocarDigitando(resposta);
            }

            @Override
            public void aoFalhar(ConversaRepository.Falha falha) {
                trocarDigitando(getApplication().getString(textoDaFalha(falha)));
            }
        });
        return true;
    }

    private void trocarDigitando(String texto) {
        // Enquanto aguarda nao entra mensagem nova, entao o "digitando" e sempre
        // a ultima da lista.
        mensagens.set(mensagens.size() - 1, Mensagem.doAssistente(texto));
        publicar();
        aguardando.setValue(false);
    }

    private void adicionar(Mensagem mensagem) {
        mensagens.add(mensagem);
        publicar();
    }

    private void publicar() {
        mensagensVisiveis.setValue(new ArrayList<>(mensagens));
    }

    @StringRes
    private static int textoDaFalha(ConversaRepository.Falha falha) {
        switch (falha) {
            case NAO_CONFIGURADO:
                return R.string.assistente_offline;
            case SEM_CONEXAO:
                return R.string.assistente_erro_conexao;
            case DEMOROU:
                return R.string.assistente_erro_demora;
            case SESSAO_EXPIRADA:
                return R.string.assistente_erro_sessao;
            case FALHA_SERVIDOR:
            default:
                return R.string.assistente_erro_servidor;
        }
    }
}
