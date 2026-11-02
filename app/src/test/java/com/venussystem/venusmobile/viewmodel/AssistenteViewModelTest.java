package com.venussystem.venusmobile.viewmodel;

import android.app.Application;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.test.core.app.ApplicationProvider;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.HumorDaVeninha;
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
        conversa.ultimoCallback.aoResponder(resposta, idDaApi, null);
    }

    @Test
    public void comecaSemMensagens() {
        assertTrue(mensagens().isEmpty());
        assertFalse(viewModel.getAguardando().getValue());
    }

    @Test
    public void abrir_acordaOServidor() {
        assertEquals(1, conversa.vezesQueAcordou);
    }

    @Test
    public void perguntar_mostraAPerguntaEODigitando() {
        assertTrue(viewModel.perguntar("  tem álcool?  "));

        List<Mensagem> lista = mensagens();
        assertEquals(2, lista.size());
        assertEquals(Mensagem.DO_USUARIO, lista.get(0).getAutor());
        assertEquals("tem álcool?", lista.get(0).getTexto());
        assertEquals(app.getString(R.string.assistente_digitando), lista.get(1).getTexto());
        assertTrue(lista.get(1).isDigitando());
        assertFalse(lista.get(0).isDigitando());
        assertTrue(viewModel.getAguardando().getValue());
        assertEquals("tem álcool?", conversa.mensagens.get(0));
    }

    @Test
    public void resposta_tomaOLugarDoDigitando() {
        conversarUmaVez("oi", "Oi! Como posso ajudar?", UID);

        assertEquals(2, mensagens().size());
        assertEquals(Mensagem.DO_ASSISTENTE, ultima().getAutor());
        assertEquals("Oi! Como posso ajudar?", ultima().getTexto());
        assertFalse(ultima().isDigitando());
        assertFalse(viewModel.getAguardando().getValue());
    }

    @Test
    public void enquantoAguarda_segundaPerguntaEIgnorada() {
        viewModel.perguntar("primeira");

        assertFalse(viewModel.perguntar("segunda"));

        assertEquals(1, conversa.mensagens.size());
        assertEquals(2, mensagens().size());
    }

    @Test
    public void perguntaVazia_eIgnorada() {
        assertFalse(viewModel.perguntar("   "));

        assertEquals(0, conversa.mensagens.size());
        assertEquals(0, mensagens().size());
    }

    @Test
    public void primeiraVaiSemId_eAsSeguintesUsamOIdDevolvido() {
        conversarUmaVez("primeira", "ok", UID);
        viewModel.perguntar("segunda");

        assertNull(conversa.conversas.get(0));
        assertEquals(UID, conversa.conversas.get(1));
    }

    @Test
    public void reabrir_trazAConversaEContinuaComOMesmoId() {
        conversarUmaVez("oi", "Olá!", UID);

        AssistenteViewModel reaberto = abrirChat();
        reaberto.perguntar("e agora?");

        List<Mensagem> lista = mensagens(reaberto);
        assertEquals("oi", lista.get(0).getTexto());
        assertEquals(Mensagem.DO_USUARIO, lista.get(0).getAutor());
        assertEquals("Olá!", lista.get(1).getTexto());
        assertEquals(Mensagem.DO_ASSISTENTE, lista.get(1).getAutor());
        assertEquals(UID, conversa.conversas.get(1));
    }

    @Test
    public void respostaQueChegaDepoisDeFecharATela_ficaGravada() {
        viewModel.perguntar("oi");
        ConversaRepository.AoResponder chegando = conversa.ultimoCallback;
        viewModel = null;

        chegando.aoResponder("Olá!", UID, null);

        List<Mensagem> lista = mensagens(abrirChat());
        assertEquals(2, lista.size());
        assertEquals("Olá!", lista.get(1).getTexto());
    }

    @Test
    public void falha_naoEGravada() {
        viewModel.perguntar("oi");
        conversa.ultimoCallback.aoFalhar(ConversaRepository.Falha.SEM_CONEXAO);

        assertEquals(0, mensagens(abrirChat()).size());
    }

    @Test
    public void sincronizarComSalva_trazOQueAndouEmOutraTela() {
        AssistenteViewModel telaDoProduto = abrirChat();
        telaDoProduto.perguntar("e este produto?");
        conversa.ultimoCallback.aoResponder("Parece bom.", UID, null);
        assertEquals(0, mensagens().size());

        viewModel.sincronizarComSalva();

        List<Mensagem> lista = mensagens();
        assertEquals(2, lista.size());
        assertEquals("e este produto?", lista.get(0).getTexto());
        assertEquals("Parece bom.", lista.get(1).getTexto());
    }

    @Test
    public void sincronizarComSalva_continuaComOIdDaConversaDaOutraTela() {
        AssistenteViewModel telaDoProduto = abrirChat();
        telaDoProduto.perguntar("oi");
        conversa.ultimoCallback.aoResponder("Ola!", UID, null);

        viewModel.sincronizarComSalva();
        viewModel.perguntar("e agora?");

        assertEquals(UID, conversa.conversas.get(1));
    }

    @Test
    public void sincronizarComSalva_enquantoAguarda_naoDerrubaAResposta() {
        viewModel.perguntar("oi");

        viewModel.sincronizarComSalva();

        assertEquals(2, mensagens().size());
        assertTrue(viewModel.getAguardando().getValue());
        conversa.ultimoCallback.aoResponder("Ola!", UID, null);
        assertEquals("Ola!", ultima().getTexto());
    }

    @Test
    public void novaConversa_limpaATelaETrocaOId() {
        conversarUmaVez("oi", "Olá!", UID);

        assertTrue(viewModel.novaConversa());
        viewModel.perguntar("de novo");

        assertEquals(2, mensagens().size());
        String idNovo = conversa.conversas.get(1);
        assertNotNull(idNovo);
        assertNotEquals(UID, idNovo);
        assertEquals(0, mensagens(abrirChat()).size());
    }

    @Test
    public void novaConversa_enquantoAguarda_eRecusada() {
        viewModel.perguntar("oi");

        assertFalse(viewModel.novaConversa());
        assertEquals(2, mensagens().size());
    }

    @Test
    public void respostaDaConversaAntiga_naoEntraNaNova() {
        viewModel.perguntar("oi");
        ConversaRepository.AoResponder antigo = conversa.ultimoCallback;

        AssistenteViewModel outraTela = abrirChat();
        outraTela.novaConversa();
        antigo.aoResponder("Olá!", UID, null);

        assertEquals(0, mensagens(abrirChat()).size());
    }

    @Test
    public void produto_vaiNaMensagemMasNaoNaBolha() {
        viewModel.definirProduto("Hidratante Firmador", "Nivea");

        viewModel.perguntar("Tem álcool?");
        conversa.ultimoCallback.aoResponder("Não tem.", UID, null);

        assertEquals("Sobre o produto \"Hidratante Firmador\" da marca Nivea: Tem álcool?",
                conversa.mensagens.get(0));
        assertEquals("Tem álcool?", mensagens().get(0).getTexto());
        assertEquals("Tem álcool?", mensagens(abrirChat()).get(0).getTexto());
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

    @Test
    public void abrir_comecaFeliz() {
        assertEquals(HumorDaVeninha.FELIZ, viewModel.getHumor().getValue());
        assertNull(viewModel.getFala().getValue());
    }

    @Test
    public void enquantoAguarda_ficaPensando() {
        viewModel.perguntar("oi");

        assertEquals(HumorDaVeninha.PENSANDO, viewModel.getHumor().getValue());
    }

    @Test
    public void respostaNeutra_voltaAFicarFeliz() {
        viewModel.perguntar("oi");
        conversa.ultimoCallback.aoResponder("Oi!", UID, "neutra");

        assertEquals(HumorDaVeninha.FELIZ, viewModel.getHumor().getValue());
    }

    @Test
    public void respostaMagoada_ficaChorando() {
        viewModel.perguntar("você é inútil");
        conversa.ultimoCallback.aoResponder("Poxa, isso me magoou.", UID, "magoada");

        assertEquals(HumorDaVeninha.CHORANDO, viewModel.getHumor().getValue());
    }

    @Test
    public void expressaoDesconhecida_ficaFeliz() {
        viewModel.perguntar("oi");
        conversa.ultimoCallback.aoResponder("Oi!", UID, "surpresa");

        assertEquals(HumorDaVeninha.FELIZ, viewModel.getHumor().getValue());
    }

    @Test
    public void falha_ficaConfusa() {
        viewModel.perguntar("oi");
        conversa.ultimoCallback.aoFalhar(ConversaRepository.Falha.DEMOROU);

        assertEquals(HumorDaVeninha.CONFUSA, viewModel.getHumor().getValue());
    }

    @Test
    public void novaConversa_voltaAFicarFeliz() {
        viewModel.perguntar("oi");
        conversa.ultimoCallback.aoFalhar(ConversaRepository.Falha.SEM_CONEXAO);

        viewModel.novaConversa();

        assertEquals(HumorDaVeninha.FELIZ, viewModel.getHumor().getValue());
    }

    @Test
    public void resposta_viraFalaUmaVez() {
        conversarUmaVez("oi", "Oi! Como posso ajudar?", UID);

        assertEquals("Oi! Como posso ajudar?", viewModel.getFala().getValue());

        viewModel.esquecerFala();
        assertNull(viewModel.getFala().getValue());
    }

    @Test
    public void falha_tambemViraFala() {
        viewModel.perguntar("oi");
        conversa.ultimoCallback.aoFalhar(ConversaRepository.Falha.SEM_CONEXAO);

        assertEquals(app.getString(R.string.assistente_erro_conexao), viewModel.getFala().getValue());
    }

    @Test
    public void reabrirAConversaSalva_naoFala() {
        conversarUmaVez("oi", "Olá!", UID);

        assertNull(abrirChat().getFala().getValue());
    }
}
