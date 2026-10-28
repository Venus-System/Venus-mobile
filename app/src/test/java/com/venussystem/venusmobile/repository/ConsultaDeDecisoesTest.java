package com.venussystem.venusmobile.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import com.venussystem.venusmobile.domain.aviso.AvisoDeAnalise;
import com.venussystem.venusmobile.model.ProdutoEmAnalise;
import com.venussystem.venusmobile.model.ProdutoEmAnalise.Situacao;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.OLDEST_SDK)
public class ConsultaDeDecisoesTest {

    private Context context;
    private List<ProdutoEmAnalise> naApi;
    private boolean apiFora;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        naApi = new ArrayList<>();
        apiFora = false;
    }

    private ConsultaDeDecisoes consulta(String arquivo) {
        SharedPreferences prefs = context.getSharedPreferences(arquivo, Context.MODE_PRIVATE);
        return new ConsultaDeDecisoes(() -> apiFora ? null : new ArrayList<>(naApi), prefs);
    }

    private static ProdutoEmAnalise produto(String id, Situacao situacao) {
        return new ProdutoEmAnalise(id, situacao, "Produto " + id, "Marca", null, null, null, null,
                situacao == Situacao.RECUSADO ? "Foto ruim" : null, situacao == Situacao.APROVADO ? 7L : null);
    }

    @Test
    public void primeiraConferencia_naoAvisaDoQueJaEstavaDecidido() {
        naApi.addAll(Arrays.asList(produto("a", Situacao.APROVADO), produto("b", Situacao.RECUSADO)));

        ConsultaDeDecisoes.Resultado resultado = consulta("t1").verificar();

        assertTrue(resultado.avisos.isEmpty());
        assertFalse(resultado.aindaAguardando);
        assertFalse(resultado.falhou);
    }

    @Test
    public void primeiraConferencia_comProdutoAguardando_continuaConferindo() {
        naApi.add(produto("a", Situacao.AGUARDANDO));

        assertTrue(consulta("t1").verificar().aindaAguardando);
    }

    @Test
    public void listaVazia_naoHaOQueEsperar() {
        ConsultaDeDecisoes.Resultado resultado = consulta("t1").verificar();

        assertTrue(resultado.avisos.isEmpty());
        assertFalse(resultado.aindaAguardando);
    }

    @Test
    public void produtoQueEraDeEsperaEVirouAprovado_avisaComOProduto() {
        ConsultaDeDecisoes consulta = consulta("t1");
        naApi.add(produto("a", Situacao.AGUARDANDO));
        consulta.verificar();

        naApi.set(0, produto("a", Situacao.APROVADO));
        ConsultaDeDecisoes.Resultado resultado = consulta.verificar();

        assertEquals(1, resultado.avisos.size());
        assertEquals("Produto aprovado", resultado.avisos.get(0).titulo);
        assertEquals(AvisoDeAnalise.Destino.PRODUTO, resultado.avisos.get(0).destino);
        assertFalse(resultado.aindaAguardando);
    }

    @Test
    public void produtoQueVirouRecusado_avisaComOMotivo() {
        ConsultaDeDecisoes consulta = consulta("t1");
        naApi.add(produto("a", Situacao.EM_ANALISE));
        consulta.verificar();

        naApi.set(0, produto("a", Situacao.RECUSADO));
        ConsultaDeDecisoes.Resultado resultado = consulta.verificar();

        assertEquals(1, resultado.avisos.size());
        assertEquals("Produto não aprovado", resultado.avisos.get(0).titulo);
        assertEquals("Produto a: Foto ruim", resultado.avisos.get(0).texto);
    }

    @Test
    public void ADecisaoNaoAvisaDeNovo() {
        ConsultaDeDecisoes consulta = consulta("t1");
        naApi.add(produto("a", Situacao.AGUARDANDO));
        consulta.verificar();
        naApi.set(0, produto("a", Situacao.APROVADO));
        consulta.verificar();

        ConsultaDeDecisoes.Resultado outraVez = consulta.verificar();

        assertTrue(outraVez.avisos.isEmpty());
    }

    @Test
    public void umaDecisaoEntreDuasConferencias_avisaCadaProduto() {
        ConsultaDeDecisoes consulta = consulta("t1");
        naApi.addAll(Arrays.asList(produto("a", Situacao.AGUARDANDO), produto("b", Situacao.AGUARDANDO),
                produto("c", Situacao.AGUARDANDO)));
        consulta.verificar();

        naApi.set(0, produto("a", Situacao.APROVADO));
        naApi.set(1, produto("b", Situacao.RECUSADO));
        ConsultaDeDecisoes.Resultado resultado = consulta.verificar();

        assertEquals(2, resultado.avisos.size());
        assertTrue(resultado.aindaAguardando);
    }

    @Test
    public void produtoEnviadoDepoisDaPrimeiraEJaDecidido_avisa() {
        ConsultaDeDecisoes consulta = consulta("t1");
        consulta.verificar();

        naApi.add(produto("novo", Situacao.APROVADO));
        ConsultaDeDecisoes.Resultado resultado = consulta.verificar();

        assertEquals(1, resultado.avisos.size());
    }

    @Test
    public void apiFora_pedeParaTentarDepoisENaoAvisaNada() {
        apiFora = true;

        ConsultaDeDecisoes.Resultado resultado = consulta("t1").verificar();

        assertTrue(resultado.falhou);
        assertTrue(resultado.avisos.isEmpty());
    }

    @Test
    public void apiForaNaPrimeiraVez_naoContaComoPrimeiraConferencia() {
        ConsultaDeDecisoes consulta = consulta("t1");
        apiFora = true;
        consulta.verificar();
        apiFora = false;
        naApi.add(produto("a", Situacao.APROVADO));

        assertTrue(consulta.verificar().avisos.isEmpty());
    }

    @Test
    public void cadaContaTemOSeuProprioHistorico() {
        ConsultaDeDecisoes ana = consulta("ana");
        naApi.add(produto("a", Situacao.AGUARDANDO));
        ana.verificar();
        naApi.set(0, produto("a", Situacao.APROVADO));

        assertTrue(consulta("bia").verificar().avisos.isEmpty());
        assertEquals(1, ana.verificar().avisos.size());
    }
}
