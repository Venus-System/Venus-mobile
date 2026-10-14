package com.venussystem.venusmobile.viewmodel;

import android.app.Application;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.test.core.app.ApplicationProvider;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.Mensagem;
import com.venussystem.venusmobile.repository.ConversaRepository;
import com.venussystem.venusmobile.repository.ConversaSalvaRepository;
import com.venussystem.venusmobile.testutil.SessaoFalsa;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.OLDEST_SDK)
public class AssistenteViewModelTest {

    private static final String UID = "uid-ana";

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    /**
     * Nao vai na rede: guarda cada envio para o teste decidir quando e como a
     * Venus "responde".
     */
    private static class ConversaFalsa extends ConversaRepository {
        final List<String> mensagens = new ArrayList<>();
        final List<String> conversas = new ArrayList<>();
        int vezesQueAcordou = 0;
        AoResponder ultimoCallback;

        ConversaFalsa() {
            super(null, new SessaoFalsa());
        }

        @Override
        public void acordar() {
            vezesQueAcordou++;
        }

        @Override
        public void enviar(String mensagem, String conversationId, AoResponder callback) {
            mensagens.add(mensagem);
            conversas.add(conversationId);
            ultimoCallback = callback;
        }
    }

    private Application app;
    private ConversaFalsa conversa;
    private AssistenteViewModel viewModel;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        conversa = new ConversaFalsa();
        viewModel = abrirChat();
    }

    /** Como abrir a tela de novo: ViewModel novo, mesma conta no aparelho. */
    private AssistenteViewModel abrirChat() {
        return new AssistenteViewModel(app, conversa, new ConversaSalvaRepository(app, UID));
    }

    private List<Mensagem> mensagens() {
        return mensagens(viewModel);
    }

    private static List<Mensagem> mensagens(AssistenteViewModel vm) {
        return vm.getMensagens().getValue();
    }

    private Mensagem ultima() {
        List<Mensagem> lista = mensagens();
        return lista.get(lista.size() - 1);
    }

    private void conversarUmaVez(String pergunta, String resposta, String idDaApi) {
        viewModel.perguntar(pergunta);
        conversa.ultimoCallback.aoResponder(resposta, idDaApi);
    }

    // ---- Abrir ----

    @Test
    public void comecaSoComASaudacao() {
        assertEquals(1, mensagens().size());
        assertEquals(app.getString(R.string.assistente_saudacao), ultima().getTexto());
        assertFalse(viewModel.getAguardando().getValue());
    }

    @Test
    public void abrir_acordaOServidor() {
        assertEquals(1, conversa.vezesQueAcordou);
    }

    // ---- Perguntar ----

    @Test
    public void perguntar_mostraAPerguntaEODigitando() {
        assertTrue(viewModel.perguntar("  tem álcool?  "));

        List<Mensagem> lista = mensagens();
        assertEquals(3, lista.size());
        assertEquals(Mensagem.DO_USUARIO, lista.get(1).getAutor());
        assertEquals("tem álcool?", lista.get(1).getTexto());
        assertEquals(app.getString(R.string.assistente_digitando), lista.get(2).getTexto());
        assertTrue(viewModel.getAguardando().getValue());
        assertEquals("tem álcool?", conversa.mensagens.get(0));
    }

    @Test
    public void resposta_tomaOLugarDoDigitando() {
        conversarUmaVez("oi", "Oi! Como posso ajudar?", UID);

        assertEquals(3, mensagens().size());
        assertEquals(Mensagem.DO_ASSISTENTE, ultima().getAutor());
        assertEquals("Oi! Como posso ajudar?", ultima().getTexto());
        assertFalse(viewModel.getAguardando().getValue());
    }

    @Test
    public void enquantoAguarda_segundaPerguntaEIgnorada() {
        viewModel.perguntar("primeira");

        assertFalse(viewModel.perguntar("segunda"));

        assertEquals(1, conversa.mensagens.size());
        assertEquals(3, mensagens().size());
    }

    @Test
    public void perguntaVazia_eIgnorada() {
        assertFalse(viewModel.perguntar("   "));

        assertEquals(0, conversa.mensagens.size());
        assertEquals(1, mensagens().size());
    }

    // ---- Id da conversa ----

    @Test
    public void primeiraVaiSemId_eAsSeguintesUsamOIdDevolvido() {
        conversarUmaVez("primeira", "ok", UID);
        viewModel.perguntar("segunda");

        assertNull(conversa.conversas.get(0));
        assertEquals(UID, conversa.conversas.get(1));
    }

    // ---- Conversa salva ----

    @Test
    public void reabrir_trazAConversaEContinuaComOMesmoId() {
        conversarUmaVez("oi", "Olá!", UID);

        AssistenteViewModel reaberto = abrirChat();
        reaberto.perguntar("e agora?");

        List<Mensagem> lista = mensagens(reaberto);
        assertEquals(app.getString(R.string.assistente_saudacao), lista.get(0).getTexto());
        assertEquals("oi", lista.get(1).getTexto());
        assertEquals(Mensagem.DO_USUARIO, lista.get(1).getAutor());
        assertEquals("Olá!", lista.get(2).getTexto());
        assertEquals(Mensagem.DO_ASSISTENTE, lista.get(2).getAutor());
        assertEquals(UID, conversa.conversas.get(1));
    }

    @Test
    public void respostaQueChegaDepoisDeFecharATela_ficaGravada() {
        viewModel.perguntar("oi");
        // O repositorio segura o callback; a tela pode ja ter fechado.
        ConversaRepository.AoResponder chegando = conversa.ultimoCallback;
        viewModel = null;

        chegando.aoResponder("Olá!", UID);

        List<Mensagem> lista = mensagens(abrirChat());
        assertEquals(3, lista.size());
        assertEquals("Olá!", lista.get(2).getTexto());
    }

    @Test
    public void falha_naoEGravada() {
        viewModel.perguntar("oi");
        conversa.ultimoCallback.aoFalhar(ConversaRepository.Falha.SEM_CONEXAO);

        assertEquals(1, mensagens(abrirChat()).size());
    }

    // ---- Nova conversa ----

    @Test
    public void novaConversa_limpaATelaETrocaOId() {
        conversarUmaVez("oi", "Olá!", UID);

        assertTrue(viewModel.novaConversa());
        viewModel.perguntar("de novo");

        assertEquals(3, mensagens().size());
        String idNovo = conversa.conversas.get(1);
        assertNotNull(idNovo);
        assertNotEquals(UID, idNovo);
        // Reabrir nao traz a conversa de antes.
        assertEquals(1, mensagens(abrirChat()).size());
    }

    @Test
    public void novaConversa_enquantoAguarda_eRecusada() {
        viewModel.perguntar("oi");

        assertFalse(viewModel.novaConversa());
        assertEquals(3, mensagens().size());
    }

    @Test
    public void respostaDaConversaAntiga_naoEntraNaNova() {
        viewModel.perguntar("oi");
        ConversaRepository.AoResponder antigo = conversa.ultimoCallback;

        // Outra tela do chat comecou uma conversa nova enquanto a resposta vinha.
        AssistenteViewModel outraTela = abrirChat();
        outraTela.novaConversa();
        antigo.aoResponder("Olá!", UID);

        assertEquals(1, mensagens(abrirChat()).size());
    }

    // ---- Produto ----

    @Test
    public void produto_vaiNaMensagemMasNaoNaBolha() {
        viewModel.definirProduto("Hidratante Firmador", "Nivea");

        viewModel.perguntar("Tem álcool?");
        conversa.ultimoCallback.aoResponder("Não tem.", UID);

        assertEquals("Sobre o produto \"Hidratante Firmador\" da marca Nivea: Tem álcool?",
                conversa.mensagens.get(0));
        assertEquals("Tem álcool?", mensagens().get(1).getTexto());
        assertEquals("Tem álcool?", mensagens(abrirChat()).get(1).getTexto());
    }

    @Test
    public void produtoSemMarca_vaiSoComONome() {
        viewModel.definirProduto("Hidratante Firmador", "  ");

        viewModel.perguntar("É vegano?");

        assertEquals("Sobre o produto \"Hidratante Firmador\": É vegano?", conversa.mensagens.get(0));
    }

    @Test
    public void nomeDeProdutoEnorme_eCortado() {
        StringBuilder nome = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            nome.append('a');
        }
        viewModel.definirProduto(nome.toString(), null);

        String enviada = viewModel.paraApi("oi");

        assertTrue(enviada.length() < 300);
        assertTrue(enviada.contains("…"));
    }

    @Test
    public void semProduto_mandaSoAPergunta() {
        assertEquals("oi", viewModel.paraApi("oi"));
    }

    // ---- Falhas ----

    @Test
    public void semUrlConfigurada_mostraOAvisoDeOffline() {
        viewModel.perguntar("oi");

        conversa.ultimoCallback.aoFalhar(ConversaRepository.Falha.NAO_CONFIGURADO);

        assertEquals(app.getString(R.string.assistente_offline), ultima().getTexto());
        assertFalse(viewModel.getAguardando().getValue());
    }

    @Test
    public void falhaDeConexao_viraMensagemDaVenusEDevolveAPergunta() {
        viewModel.perguntar("oi");

        conversa.ultimoCallback.aoFalhar(ConversaRepository.Falha.SEM_CONEXAO);

        assertEquals(Mensagem.DO_ASSISTENTE, ultima().getAutor());
        assertEquals(app.getString(R.string.assistente_erro_conexao), ultima().getTexto());
        assertEquals("oi", viewModel.getPerguntaQueFalhou().getValue());

        viewModel.esquecerPerguntaQueFalhou();
        assertNull(viewModel.getPerguntaQueFalhou().getValue());
    }

    @Test
    public void limiteDeMensagens_temAvisoProprio() {
        viewModel.perguntar("oi");

        conversa.ultimoCallback.aoFalhar(ConversaRepository.Falha.MUITAS_MENSAGENS);

        assertEquals(app.getString(R.string.assistente_erro_limite), ultima().getTexto());
    }

    @Test
    public void mensagemRecusada_temAvisoProprio() {
        viewModel.perguntar("oi");

        conversa.ultimoCallback.aoFalhar(ConversaRepository.Falha.MENSAGEM_INVALIDA);

        assertEquals(app.getString(R.string.assistente_erro_mensagem), ultima().getTexto());
    }
}
