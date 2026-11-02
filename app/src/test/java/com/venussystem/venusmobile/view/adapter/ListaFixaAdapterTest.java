package com.venussystem.venusmobile.view.adapter;

import android.app.Activity;
import android.content.Intent;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.view.ProdutosCurtidosActivity;
import com.venussystem.venusmobile.view.ProdutosEmAnaliseActivity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.OLDEST_SDK)
public class ListaFixaAdapterTest {

    private Activity tela;
    private ListaFixaAdapter adapter;

    @Before
    public void montar() {
        tela = Robolectric.buildActivity(Activity.class).setup().get();
        adapter = new ListaFixaAdapter();
    }

    private ListaFixaAdapter.CartaoViewHolder cartao(int posicao) {
        ListaFixaAdapter.CartaoViewHolder holder =
                adapter.onCreateViewHolder(new FrameLayout(tela), 0);
        adapter.onBindViewHolder(holder, posicao);
        return holder;
    }

    private static String nome(ListaFixaAdapter.CartaoViewHolder holder) {
        return ((TextView) holder.itemView.findViewById(R.id.textNomeLista)).getText().toString();
    }

    private String telaAbertaAoTocar(ListaFixaAdapter.CartaoViewHolder holder) {
        holder.itemView.performClick();
        Intent aberta = shadowOf(tela).getNextStartedActivity();
        return aberta.getComponent().getClassName();
    }

    @Test
    public void saoDuasListasFixas() {
        assertEquals(2, adapter.getItemCount());
    }

    @Test
    public void primeira_ProdutosCurtidos_abreATelaDeles() {
        ListaFixaAdapter.CartaoViewHolder holder = cartao(0);

        assertEquals(tela.getString(R.string.curtidos_titulo), nome(holder));
        assertEquals(ProdutosCurtidosActivity.class.getName(), telaAbertaAoTocar(holder));
    }

    @Test
    public void segunda_ProdutosEmAnalise_abreATelaDeles() {
        ListaFixaAdapter.CartaoViewHolder holder = cartao(1);

        assertEquals(tela.getString(R.string.analise_titulo), nome(holder));
        assertEquals(ProdutosEmAnaliseActivity.class.getName(), telaAbertaAoTocar(holder));
    }

    @Test
    public void separador_mostraONomeDaSecao() {
        TituloSecaoAdapter titulo = new TituloSecaoAdapter(R.string.listas_secao_fixas);
        TituloSecaoAdapter.TituloViewHolder holder =
                titulo.onCreateViewHolder(new FrameLayout(tela), 0);
        titulo.onBindViewHolder(holder, 0);

        assertEquals(1, titulo.getItemCount());
        assertEquals("Listas fixas", holder.texto.getText().toString());
    }

    @Test
    public void avisoDeVazio_soApareceQuandoPedido() {
        AvisoListasVazioAdapter aviso = new AvisoListasVazioAdapter();
        assertEquals(0, aviso.getItemCount());

        aviso.mostrar(true);
        assertEquals(1, aviso.getItemCount());

        aviso.mostrar(false);
        assertEquals(0, aviso.getItemCount());
    }
}
