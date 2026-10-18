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
import com.venussystem.venusmobile.model.Mensagem;
import com.venussystem.venusmobile.repository.ConversaRepository;
import com.venussystem.venusmobile.repository.ConversaSalvaRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Estado da conversa com a Venus. Fica no ViewModel, e nao na Activity, porque
 * uma resposta pode levar mais de um minuto: girar a tela no meio disso nao
 * pode apagar a conversa nem perder a resposta que esta chegando.
 *
 * A conversa e uma so por conta e continua entre aberturas do chat: as
 * mensagens ficam no ConversaSalvaRepository e o historico, na API. "Nova
 * conversa" comeca outra dos dois lados.
 */
public class AssistenteViewModel extends AndroidViewModel {

    // A API nao tem campo de produto: quando o chat abre por um produto, a
    // pergunta vai com o nome dele na frente, para a Venus saber do que se fala.
    // A bolha mostra so o que a pessoa escreveu.
    private static final String FORMATO_COM_MARCA = "Sobre o produto \"%s\" da marca %s: %s";
    private static final String FORMATO_SEM_MARCA = "Sobre o produto \"%s\": %s";

    // Nome de catalogo as vezes vem enorme; cortado, cabe folgado nos 4000
    // caracteres que a API aceita, junto com os 3500 do campo de texto.
    @VisibleForTesting
    static final int LIMITE_NOME_PRODUTO = 120;

    private final ConversaRepository conversa;
    private final ConversaSalvaRepository salva;

    // Null ate a primeira resposta: o contrato pede para omitir o id na
    // primeira mensagem e reenviar o que a API devolver.
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

    /**
     * A ultima pergunta que nao teve resposta, para a tela devolver ao campo e
     * a pessoa so tocar em enviar de novo. Null depois de usada.
     */
    public LiveData<String> getPerguntaQueFalhou() {
        return perguntaQueFalhou;
    }

    public void esquecerPerguntaQueFalhou() {
        perguntaQueFalhou.setValue(null);
    }

    /**
     * Rele a conversa salva. A aba do chat chama ao abrir: a conversa pode ter
     * andado em outra tela (a do produto) enquanto este ViewModel, que vive
     * tanto quanto a tela principal, ficava parado.
     *
     * Nao faz nada enquanto a Venus responde: a resposta que esta vindo e
     * desta conversa, e reler agora a derrubaria da tela.
     */
    public void sincronizarComSalva() {
        if (Boolean.TRUE.equals(aguardando.getValue())) {
            return;
        }
        mostrar(salva.carregar());
    }

    /**
     * O produto de onde o chat foi aberto, ou null no chat geral. Vale para
     * todas as perguntas desta tela.
     */
    public void definirProduto(@Nullable String nome, @Nullable String marca) {
        nomeProduto = vazioParaNull(nome);
        marcaProduto = vazioParaNull(marca);
        if (nomeProduto != null && nomeProduto.length() > LIMITE_NOME_PRODUTO) {
            nomeProduto = nomeProduto.substring(0, LIMITE_NOME_PRODUTO).trim() + "…";
        }
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
        perguntaQueFalhou.setValue(null);

        String idEnviado = conversationId;
        conversa.enviar(paraApi(pergunta), idEnviado, new ConversaRepository.AoResponder() {
            @Override
            public void aoResponder(String resposta, @Nullable String idUsado) {
                // Grava antes de mostrar, e mesmo se a tela ja fechou: a resposta
                // aparece quando a pessoa reabrir o chat.
                salva.adicionarPar(idEnviado, pergunta, resposta, idUsado);
                conversationId = idUsado;
                trocarDigitando(resposta);
            }

            @Override
            public void aoFalhar(ConversaRepository.Falha falha) {
                trocarDigitando(getApplication().getString(textoDaFalha(falha)));
                perguntaQueFalhou.setValue(pergunta);
            }
        });
        return true;
    }

    /**
     * Apaga a conversa do aparelho e comeca outra na API.
     *
     * @return false enquanto a Venus responde: a resposta que esta vindo e da
     * conversa atual.
     */
    public boolean novaConversa() {
        if (Boolean.TRUE.equals(aguardando.getValue())) {
            return false;
        }
        salva.novaConversa();
        perguntaQueFalhou.setValue(null);
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
        // A saudacao abre toda conversa, mas nao e gravada: e da tela.
        mensagens.add(Mensagem.doAssistente(getApplication().getString(R.string.assistente_saudacao)));
        mensagens.addAll(salvaAgora.mensagens);
        publicar();
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
