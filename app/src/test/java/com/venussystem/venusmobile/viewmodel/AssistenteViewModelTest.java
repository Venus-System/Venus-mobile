package com.venussystem.venusmobile.viewmodel;

import android.app.Application;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.test.core.app.ApplicationProvider;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.Mensagem;
import com.venussystem.venusmobile.repository.ConversaRepository;
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
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.OLDEST_SDK)
public class AssistenteViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    /**
     * Nao vai na rede: guarda cada envio para o teste decidir quando e como a
     * Venus "responde".
     */
    private static class ConversaFalsa extends ConversaRepository {
        final List<String> mensagens = new ArrayList<>();
        final List<String> conversas = new ArrayList<>();
        AoResponder ultimoCallback;

        ConversaFalsa() {
            super(null, new SessaoFalsa(), () -> null);
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
        viewModel = new AssistenteViewModel(app, conversa);
    }

    private List<Mensagem> mensagens() {
        return viewModel.getMensagens().getValue();
    }

    private Mensagem ultima() {
        List<Mensagem> lista = mensagens();
        return lista.get(lista.size() - 1);
    }

    @Test
    public void comecaSoComASaudacao() {
        assertEquals(1, mensagens().size());
        assertEquals(app.getString(R.string.assistente_saudacao), ultima().getTexto());
        assertFalse(viewModel.getAguardando().getValue());
    }

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
        viewModel.perguntar("oi");

        conversa.ultimoCallback.aoResponder("Oi! Como posso ajudar?");

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
    public void mesmaConversa_usaSempreOMesmoId() {
        viewModel.perguntar("primeira");
        conversa.ultimoCallback.aoResponder("ok");
        viewModel.perguntar("segunda");

        assertEquals(2, conversa.conversas.size());
        assertEquals(conversa.conversas.get(0), conversa.conversas.get(1));
    }

    @Test
    public void conversaNova_ganhaOutroId() {
        viewModel.perguntar("oi");
        AssistenteViewModel outra = new AssistenteViewModel(app, conversa);
        outra.perguntar("oi");

        assertFalse(conversa.conversas.get(0).equals(conversa.conversas.get(1)));
    }

    @Test
    public void semUrlConfigurada_mostraOAvisoDeOffline() {
        viewModel.perguntar("oi");

        conversa.ultimoCallback.aoFalhar(ConversaRepository.Falha.NAO_CONFIGURADO);

        assertEquals(app.getString(R.string.assistente_offline), ultima().getTexto());
        assertFalse(viewModel.getAguardando().getValue());
    }

    @Test
    public void falhaDeConexao_viraMensagemDaVenus() {
        viewModel.perguntar("oi");

        conversa.ultimoCallback.aoFalhar(ConversaRepository.Falha.SEM_CONEXAO);

        assertEquals(Mensagem.DO_ASSISTENTE, ultima().getAutor());
        assertEquals(app.getString(R.string.assistente_erro_conexao), ultima().getTexto());
    }

    @Test
    public void perguntaVazia_eIgnorada() {
        assertFalse(viewModel.perguntar("   "));

        assertEquals(0, conversa.mensagens.size());
        assertEquals(1, mensagens().size());
    }
}
