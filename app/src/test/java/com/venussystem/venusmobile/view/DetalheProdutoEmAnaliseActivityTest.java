package com.venussystem.venusmobile.view;

import android.content.Intent;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.ProdutoEmAnalise;
import com.venussystem.venusmobile.model.ProdutoEmAnalise.Situacao;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.LocalDate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.OLDEST_SDK)
public class DetalheProdutoEmAnaliseActivityTest {

    private static ProdutoEmAnalise produto(Situacao situacao, String nome, String marca,
                                            String motivo, Long produtoId) {
        return new ProdutoEmAnalise("1", situacao, nome, marca, null, null,
                LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 3), motivo, produtoId);
    }

    private static DetalheProdutoEmAnaliseActivity abrir(ProdutoEmAnalise produto) {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                DetalheProdutoEmAnaliseActivity.class);
        intent.putExtra(DetalheProdutoEmAnaliseActivity.EXTRA_PRODUTO, produto);
        return Robolectric.buildActivity(DetalheProdutoEmAnaliseActivity.class, intent)
                .setup().get();
    }

    private static String texto(DetalheProdutoEmAnaliseActivity tela, int id) {
        return ((TextView) tela.findViewById(id)).getText().toString();
    }

    @Test
    public void recusado_mostraOMotivoEADataDaRecusa_semVerProduto() {
        DetalheProdutoEmAnaliseActivity tela = abrir(produto(Situacao.RECUSADO,
                "Hidratante X", "Marca Y", "Foto do verso ilegível", null));

        assertEquals("Recusado", texto(tela, R.id.textSituacao));
        assertEquals("Foto do verso ilegível", texto(tela, R.id.textMotivo));
        assertEquals(View.VISIBLE, tela.findViewById(R.id.blocoMotivo).getVisibility());
        assertEquals("Enviado em 02/10/2026\nRecusado em 03/10/2026", texto(tela, R.id.textDatas));
        assertEquals(View.GONE, tela.findViewById(R.id.btnVerProduto).getVisibility());
    }

    @Test
    public void jaNoCatalogo_verProdutoAbreATelaDoProduto() {
        DetalheProdutoEmAnaliseActivity tela = abrir(produto(Situacao.APROVADO,
                "Cuide-se Bem Rosa", "oBoticario", "Ok", 57L));

        assertEquals(View.GONE, tela.findViewById(R.id.blocoMotivo).getVisibility());
        View verProduto = tela.findViewById(R.id.btnVerProduto);
        assertEquals(View.VISIBLE, verProduto.getVisibility());

        verProduto.performClick();

        Intent aberta = shadowOf(tela).getNextStartedActivity();
        assertEquals(DetalheProdutoActivity.class.getName(), aberta.getComponent().getClassName());
        assertEquals(57L, aberta.getLongExtra(DetalheProdutoActivity.EXTRA_ID, -1));
    }

    @Test
    public void semNomeNemMarca_mostraProdutoSemNomeEEscondeAMarca() {
        DetalheProdutoEmAnaliseActivity tela = abrir(produto(Situacao.AGUARDANDO,
                null, null, null, null));

        assertEquals("Produto sem nome", texto(tela, R.id.textNome));
        assertEquals(View.GONE, tela.findViewById(R.id.textMarca).getVisibility());
        assertEquals("Aguardando análise", texto(tela, R.id.textSituacao));
        assertEquals("Enviado em 02/10/2026", texto(tela, R.id.textDatas));
    }

    @Test
    public void semProdutoNoIntent_fecha() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                DetalheProdutoEmAnaliseActivity.class);

        DetalheProdutoEmAnaliseActivity tela = Robolectric
                .buildActivity(DetalheProdutoEmAnaliseActivity.class, intent).setup().get();

        assertTrue(tela.isFinishing());
    }
}
