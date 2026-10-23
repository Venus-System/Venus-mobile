package com.venussystem.venusmobile.repository;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.OLDEST_SDK)
public class AcessosRecentesRepositoryTest {

    private Context context;
    // O relogio do teste: cada acesso anda um segundo, como se a pessoa
    // abrisse um produto depois do outro.
    private final long[] agora = {1_000L};
    private AcessosRecentesRepository acessos;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        acessos = comRelogio("uid-ana");
    }

    private AcessosRecentesRepository comRelogio(String uid) {
        return new AcessosRecentesRepository(context, uid, () -> agora[0] += 1_000L);
    }

    private static List<Long> ids(List<AcessosRecentesRepository.Acesso> lista) {
        List<Long> ids = new ArrayList<>();
        for (AcessosRecentesRepository.Acesso acesso : lista) {
            ids.add(acesso.produtoId);
        }
        return ids;
    }

    @Test
    public void semNadaGravado_comecaVazio() {
        assertTrue(acessos.listar().isEmpty());
    }

    @Test
    public void guardaOIdEOMomentoDoAcesso() {
        acessos.registrar(7L);

        List<AcessosRecentesRepository.Acesso> lista = acessos.listar();

        assertEquals(1, lista.size());
        assertEquals(7L, lista.get(0).produtoId);
        assertEquals(2_000L, lista.get(0).momento);
    }

    @Test
    public void omaisRecenteVemPrimeiro() {
        acessos.registrar(1L);
        acessos.registrar(2L);
        acessos.registrar(3L);

        assertEquals(Arrays.asList(3L, 2L, 1L), ids(acessos.listar()));
    }

    @Test
    public void reabrirOMesmoProduto_sobeParaOTopoSemDuplicar() {
        acessos.registrar(1L);
        acessos.registrar(2L);
        acessos.registrar(3L);

        acessos.registrar(1L);

        List<AcessosRecentesRepository.Acesso> lista = acessos.listar();
        assertEquals(Arrays.asList(1L, 3L, 2L), ids(lista));
        // O momento e o da ultima abertura, nao o da primeira.
        assertEquals(5_000L, lista.get(0).momento);
    }

    @Test
    public void passouDoLimite_saemOsMaisAntigos() {
        int total = AcessosRecentesRepository.LIMITE + 5;
        for (long id = 1; id <= total; id++) {
            acessos.registrar(id);
        }

        List<Long> ids = ids(acessos.listar());

        assertEquals(AcessosRecentesRepository.LIMITE, ids.size());
        assertEquals(Long.valueOf(total), ids.get(0));
        assertEquals(Long.valueOf(6L), ids.get(ids.size() - 1));
    }

    @Test
    public void reabrirNoLimite_naoDerrubaNinguem() {
        for (long id = 1; id <= AcessosRecentesRepository.LIMITE; id++) {
            acessos.registrar(id);
        }

        acessos.registrar(1L);

        List<Long> ids = ids(acessos.listar());
        assertEquals(AcessosRecentesRepository.LIMITE, ids.size());
        assertEquals(Long.valueOf(1L), ids.get(0));
        assertTrue(ids.contains(2L));
    }

    @Test
    public void cadaContaVeOsSeus() {
        acessos.registrar(1L);

        AcessosRecentesRepository daBia = comRelogio("uid-bia");
        assertTrue(daBia.listar().isEmpty());

        daBia.registrar(9L);
        assertEquals(Collections.singletonList(1L), ids(acessos.listar()));
        assertEquals(Collections.singletonList(9L), ids(daBia.listar()));
    }

    @Test
    public void continuaGravadoDepoisDeRecriarORepositorio() {
        acessos.registrar(4L);
        acessos.registrar(5L);

        AcessosRecentesRepository reaberto = new AcessosRecentesRepository(context, "uid-ana");

        assertEquals(Arrays.asList(5L, 4L), ids(reaberto.listar()));
    }

    @Test
    public void idInvalido_naoEntraNoHistorico() {
        acessos.registrar(0L);
        acessos.registrar(-3L);

        assertTrue(acessos.listar().isEmpty());
    }

    @Test
    public void arquivoEstragado_naoTravaEComecaDoZero() {
        DadosDaConta.prefs(context, AcessosRecentesRepository.ARQUIVO, "uid-ana")
                .edit().putString("acessos", "[isso nao e json").commit();

        assertTrue(acessos.listar().isEmpty());

        acessos.registrar(8L);
        assertEquals(Collections.singletonList(8L), ids(acessos.listar()));
    }
}
