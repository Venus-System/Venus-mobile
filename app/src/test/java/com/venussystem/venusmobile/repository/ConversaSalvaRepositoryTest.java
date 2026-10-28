package com.venussystem.venusmobile.repository;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.venussystem.venusmobile.model.Mensagem;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.OLDEST_SDK)
public class ConversaSalvaRepositoryTest {

    private Context context;
    private ConversaSalvaRepository conversa;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        conversa = new ConversaSalvaRepository(context, "uid-ana");
    }

    @Test
    public void semNadaGravado_comecaVaziaESemId() {
        ConversaSalvaRepository.Conversa salva = conversa.carregar();

        assertNull(salva.conversationId);
        assertTrue(salva.mensagens.isEmpty());
    }

    @Test
    public void par_voltaNaOrdemComAutorEId() {
        assertTrue(conversa.adicionarPar(null, "tem álcool?", "Não tem.", "uid-ana"));

        ConversaSalvaRepository.Conversa salva =
                new ConversaSalvaRepository(context, "uid-ana").carregar();

        assertEquals("uid-ana", salva.conversationId);
        assertEquals(2, salva.mensagens.size());
        assertEquals(Mensagem.DO_USUARIO, salva.mensagens.get(0).getAutor());
        assertEquals("tem álcool?", salva.mensagens.get(0).getTexto());
        assertEquals(Mensagem.DO_ASSISTENTE, salva.mensagens.get(1).getAutor());
        assertEquals("Não tem.", salva.mensagens.get(1).getTexto());
    }

    @Test
    public void cadaContaVeASua() {
        conversa.adicionarPar(null, "oi", "Olá!", "uid-ana");

        ConversaSalvaRepository daBia = new ConversaSalvaRepository(context, "uid-bia");

        assertTrue(daBia.carregar().mensagens.isEmpty());
        assertNull(daBia.carregar().conversationId);
    }

    @Test
    public void passouDoLimite_saemAsMaisAntigas() {
        int pares = ConversaSalvaRepository.LIMITE_MENSAGENS / 2 + 5;
        String id = null;
        for (int i = 0; i < pares; i++) {
            conversa.adicionarPar(id, "pergunta " + i, "resposta " + i, "uid-ana");
            id = "uid-ana";
        }

        List<Mensagem> mensagens = conversa.carregar().mensagens;

        assertEquals(ConversaSalvaRepository.LIMITE_MENSAGENS, mensagens.size());
        assertEquals("pergunta 5", mensagens.get(0).getTexto());
        assertEquals("resposta " + (pares - 1), mensagens.get(mensagens.size() - 1).getTexto());
    }

    @Test
    public void parDeOutraConversa_eDescartado() {
        conversa.adicionarPar(null, "oi", "Olá!", "uid-ana");

        assertFalse(conversa.adicionarPar("conversa-velha", "e ai?", "Tudo bem.", "conversa-velha"));

        ConversaSalvaRepository.Conversa salva = conversa.carregar();
        assertEquals(2, salva.mensagens.size());
        assertEquals("uid-ana", salva.conversationId);
    }

    @Test
    public void novaConversa_limpaEGravaOutroId() {
        conversa.adicionarPar(null, "oi", "Olá!", "uid-ana");

        String novo = conversa.novaConversa();

        ConversaSalvaRepository.Conversa salva = conversa.carregar();
        assertNotNull(novo);
        assertNotEquals("uid-ana", novo);
        assertEquals(novo, salva.conversationId);
        assertTrue(salva.mensagens.isEmpty());
    }

    @Test
    public void apagar_esqueceTudo() {
        conversa.adicionarPar(null, "oi", "Olá!", "uid-ana");

        conversa.apagar();

        ConversaSalvaRepository.Conversa salva =
                new ConversaSalvaRepository(context, "uid-ana").carregar();
        assertNull(salva.conversationId);
        assertTrue(salva.mensagens.isEmpty());
    }

    @Test
    public void conversaDoArquivoAntigo_vemParaOBanco() {
        DadosDaConta.prefs(context, ConversaSalvaRepository.ARQUIVO, "uid-bia").edit()
                .putString("conversa", "{\"conversationId\":\"conv-1\",\"mensagens\":["
                        + "{\"autor\":" + Mensagem.DO_USUARIO + ",\"texto\":\"oi\"},"
                        + "{\"autor\":" + Mensagem.DO_ASSISTENTE + ",\"texto\":\"Olá!\"}]}")
                .commit();

        ConversaSalvaRepository.Conversa salva =
                new ConversaSalvaRepository(context, "uid-bia").carregar();

        assertEquals("conv-1", salva.conversationId);
        assertEquals(2, salva.mensagens.size());
        assertEquals("oi", salva.mensagens.get(0).getTexto());
        assertEquals(Mensagem.DO_ASSISTENTE, salva.mensagens.get(1).getAutor());
        assertTrue(DadosDaConta.prefs(context, ConversaSalvaRepository.ARQUIVO, "uid-bia")
                .getAll().isEmpty());
    }

    @Test
    public void arquivoAntigoEstragado_naoTravaOChat() {
        DadosDaConta.prefs(context, ConversaSalvaRepository.ARQUIVO, "uid-bia")
                .edit().putString("conversa", "{isso nao e json").commit();

        ConversaSalvaRepository daBia = new ConversaSalvaRepository(context, "uid-bia");

        assertTrue(daBia.carregar().mensagens.isEmpty());
        assertTrue(daBia.adicionarPar(null, "oi", "Olá!", "uid-bia"));
    }
}
