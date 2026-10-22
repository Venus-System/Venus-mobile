package com.venussystem.venusmobile.repository;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.test.core.app.ApplicationProvider;

import com.venussystem.venusmobile.model.ItemHistorico;
import com.venussystem.venusmobile.model.Produto;
import com.venussystem.venusmobile.testutil.LiveDataEspera;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.OLDEST_SDK)
public class HistoricoCatalogoRepositoryTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private SharedPreferences preferencias;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        preferencias = context.getSharedPreferences(
                "historico_catalogo_teste",
                Context.MODE_PRIVATE
        );
        preferencias.edit().clear().commit();
    }

    @Test
    public void acessoFicaPersistidoLocalmente() {
        HistoricoCatalogoRepository primeiro = novoRepositorio();
        primeiro.registrarAcesso(produto(12L, "Produto local"));

        HistoricoCatalogoRepository segundo = novoRepositorio();
        segundo.carregar();

        LiveDataEspera.aguardarValor(
                segundo.getHistorico(),
                lista -> lista != null && lista.size() == 1
        );
        List<ItemHistorico> entradas = segundo.getHistorico().getValue();
        assertNotNull(entradas);
        assertEquals(1, entradas.size());
        assertEquals(Long.valueOf(12L), entradas.get(0).getProduto().getId());
        assertEquals("Produto local", entradas.get(0).getProduto().getName());
    }

    @Test
    public void novoAcessoMoveProdutoParaOTopoSemDuplicar() {
        HistoricoCatalogoRepository repositorio = novoRepositorio();
        repositorio.registrarAcesso(produto(12L, "Primeiro"));
        repositorio.registrarAcesso(produto(34L, "Segundo"));
        repositorio.registrarAcesso(produto(12L, "Primeiro atualizado"));

        List<ItemHistorico> entradas = repositorio.getHistorico().getValue();
        assertNotNull(entradas);
        assertEquals(2, entradas.size());
        assertEquals(Long.valueOf(12L), entradas.get(0).getProduto().getId());
        assertEquals("Primeiro atualizado", entradas.get(0).getProduto().getName());
        assertEquals(Long.valueOf(34L), entradas.get(1).getProduto().getId());
    }

    private HistoricoCatalogoRepository novoRepositorio() {
        return new HistoricoCatalogoRepository(preferencias);
    }

    private Produto produto(long id, String nome) {
        return new Produto(id, nome, "Marca", 80, null, null, null);
    }
}
