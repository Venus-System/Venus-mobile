package com.venussystem.venusmobile.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.HumorDaVeninha;
import com.venussystem.venusmobile.model.Mensagem;
import com.venussystem.venusmobile.repository.ConversaRepository;
import com.venussystem.venusmobile.repository.ConversaSalvaRepository;

import java.util.ArrayList;
import java.util.List;

public class AssistenteViewModel extends AndroidViewModel {
    private static final String FORMATO_COM_MARCA = "Sobre o produto \"%s\" da marca %s: %s";
    private static final String FORMATO_SEM_MARCA = "Sobre o produto \"%s\": %s";

    @VisibleForTesting
    static final int LIMITE_NOME_PRODUTO = 120;

    private final ConversaRepository conversa;
    private final ConversaSalvaRepository salva;

    @Nullable
    private String conversationId;

    @Nullable
    private String nomeProduto;
    @Nullable
    private String marcaProduto;

    private final List<Mensagem> mensagens = new ArrayList<>();
    private final MutableLiveData<List<Mensagem>> mensagensVisiveis = new MutableLiveData<>();
    private final MutableLiveData<Boolean> aguardando = new MutableLiveData<>(false);
    private final MutableLiveData<String> perguntaQueFalhou = new MutableLiveData<>();
    private final MutableLiveData<HumorDaVeninha> humor = new MutableLiveData<>(HumorDaVeninha.FELIZ);
    private final MutableLiveData<String> fala = new MutableLiveData<>();

    public AssistenteViewModel(@NonNull Application application) {
        this(application, new ConversaRepository(), new ConversaSalvaRepository(application));
    }

    @VisibleForTesting
    AssistenteViewModel(@NonNull Application application, ConversaRepository conversa,
                        ConversaSalvaRepository salva) {
        super(application);
        this.conversa = conversa;
        this.salva = salva;
        mostrar(salva.carregar());
        conversa.acordar();
    }

    public LiveData<List<Mensagem>> getMensagens() {
        return mensagensVisiveis;
    }

    public LiveData<Boolean> getAguardando() {
        return aguardando;
    }

    public LiveData<String> getPerguntaQueFalhou() {
        return perguntaQueFalhou;
    }

    public void esquecerPerguntaQueFalhou() {
        perguntaQueFalhou.setValue(null);
    }

    public LiveData<HumorDaVeninha> getHumor() {
        return humor;
    }

    public LiveData<String> getFala() {
        return fala;
    }

    public void esquecerFala() {
        fala.setValue(null);
    }

    public void sincronizarComSalva() {
        if (Boolean.TRUE.equals(aguardando.getValue())) {
            return;
        }
        mostrar(salva.carregar());
    }

    public void definirProduto(@Nullable String nome, @Nullable String marca) {
        nomeProduto = vazioParaNull(nome);
        marcaProduto = vazioParaNull(marca);
        if (nomeProduto != null && nomeProduto.length() > LIMITE_NOME_PRODUTO) {
            nomeProduto = nomeProduto.substring(0, LIMITE_NOME_PRODUTO).trim() + "…";
        }
    }

    public boolean perguntar(String texto) {
        String pergunta = texto == null ? "" : texto.trim();
        if (pergunta.isEmpty() || Boolean.TRUE.equals(aguardando.getValue())) {
            return false;
        }

        adicionar(Mensagem.doUsuario(pergunta));
        adicionar(Mensagem.digitando(getApplication().getString(R.string.assistente_digitando)));
        aguardando.setValue(true);
        perguntaQueFalhou.setValue(null);
        humor.setValue(HumorDaVeninha.PENSANDO);

        String idEnviado = conversationId;
        conversa.enviar(paraApi(pergunta), idEnviado, new ConversaRepository.AoResponder() {
            @Override
            public void aoResponder(String resposta, @Nullable String idUsado,
                                    @Nullable String expressao) {
                salva.adicionarPar(idEnviado, pergunta, resposta, idUsado);
                conversationId = idUsado;
                humor.setValue(HumorDaVeninha.daExpressao(expressao));
                trocarDigitando(resposta);
            }

            @Override
            public void aoFalhar(ConversaRepository.Falha falha) {
                humor.setValue(HumorDaVeninha.CONFUSA);
                trocarDigitando(getApplication().getString(textoDaFalha(falha)));
                perguntaQueFalhou.setValue(pergunta);
            }
        });
        return true;
    }

    public boolean novaConversa() {
        if (Boolean.TRUE.equals(aguardando.getValue())) {
            return false;
        }
        salva.novaConversa();
        perguntaQueFalhou.setValue(null);
        humor.setValue(HumorDaVeninha.FELIZ);
        mostrar(salva.carregar());
        return true;
    }

    @VisibleForTesting
    String paraApi(String pergunta) {
        if (nomeProduto == null) {
            return pergunta;
        }
        return marcaProduto == null
                ? String.format(FORMATO_SEM_MARCA, nomeProduto, pergunta)
                : String.format(FORMATO_COM_MARCA, nomeProduto, marcaProduto, pergunta);
    }

    private void mostrar(ConversaSalvaRepository.Conversa salvaAgora) {
        conversationId = salvaAgora.conversationId;
        mensagens.clear();
        mensagens.addAll(salvaAgora.mensagens);
        publicar();
    }

    private void trocarDigitando(String texto) {
        mensagens.set(mensagens.size() - 1, Mensagem.doAssistente(texto));
        fala.setValue(texto);
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

    @Nullable
    private static String vazioParaNull(@Nullable String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            return null;
        }
        return texto.trim();
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
            case MUITAS_MENSAGENS:
                return R.string.assistente_erro_limite;
            case MENSAGEM_INVALIDA:
                return R.string.assistente_erro_mensagem;
            case FALHA_SERVIDOR:
            default:
                return R.string.assistente_erro_servidor;
        }
    }
}
