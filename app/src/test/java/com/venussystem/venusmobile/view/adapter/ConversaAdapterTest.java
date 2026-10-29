package com.venussystem.venusmobile.view.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.Mensagem;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.Duration;
import java.util.Arrays;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.OLDEST_SDK)
public class ConversaAdapterTest {

    @Test
    public void pontosDoDigitando_vaoDeZeroATresEVoltam() {
        assertEquals(0, ConversaAdapter.pontosVisiveis(0));
        assertEquals(1, ConversaAdapter.pontosVisiveis(1));
        assertEquals(2, ConversaAdapter.pontosVisiveis(2));
        assertEquals(3, ConversaAdapter.pontosVisiveis(3));
        assertEquals(0, ConversaAdapter.pontosVisiveis(4));
        assertEquals(2, ConversaAdapter.pontosVisiveis(10));
    }

    @Test
    public void respostaQueChega_ficaNaBolhaNoLugarDoDigitando() {
        Activity tela = Robolectric.buildActivity(Activity.class).setup().get();
        RecyclerView lista = new RecyclerView(tela);
        lista.setLayoutManager(new LinearLayoutManager(tela));
        ConversaAdapter adapter = new ConversaAdapter();
        lista.setAdapter(adapter);
        tela.setContentView(lista, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        Mensagem pergunta = Mensagem.doUsuario("Serve para pele oleosa?");
        adapter.definir(Arrays.asList(pergunta, Mensagem.digitando("Digitando...")));
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));

        adapter.definir(Arrays.asList(pergunta, Mensagem.doAssistente("Serve, sim.")));
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));

        RecyclerView.ViewHolder bolha = lista.findViewHolderForAdapterPosition(1);
        assertNotNull(bolha);
        TextView texto = bolha.itemView.findViewById(R.id.textMensagem);
        assertEquals("Serve, sim.", texto.getText().toString());
    }
}
