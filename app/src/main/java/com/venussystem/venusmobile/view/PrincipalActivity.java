package com.venussystem.venusmobile.view;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.DrawableRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.lembrete.Lembretes;
import com.venussystem.venusmobile.repository.DecisoesAgenda;
import com.venussystem.venusmobile.repository.FotoPerfilRepository;
import com.venussystem.venusmobile.repository.ReenvioDeScanAgenda;
import com.venussystem.venusmobile.repository.SincronizacaoCurtidos;
import com.venussystem.venusmobile.repository.SincronizacaoListas;
import com.venussystem.venusmobile.repository.SincronizacaoRepository;
import com.venussystem.venusmobile.view.fragment.AssistenteFragment;
import com.venussystem.venusmobile.view.fragment.InicioFragment;
import com.venussystem.venusmobile.view.fragment.ListasFragment;
import com.venussystem.venusmobile.view.fragment.PerfilFragment;

import java.util.ArrayList;
import java.util.List;

public class PrincipalActivity extends AppCompatActivity {
    private static final long DURACAO_ANIMACAO = 260L;
    private static final float ESCALA_ATIVO = 1.18f;

    private static final String ESTADO_ITEM_ATIVO = "item_ativo";

    private final List<View> itens = new ArrayList<>();
    private View indicador;
    private View itemAtivo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_principal);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets teclado = insets.getInsets(WindowInsetsCompat.Type.ime());
            boolean tecladoAberto = teclado.bottom > 0;

            int rodape = tecladoAberto ? View.GONE : View.VISIBLE;
            findViewById(R.id.barraNavegacao).setVisibility(rodape);
            findViewById(R.id.btnScan).setVisibility(rodape);

            v.setPadding(systemBars.left, systemBars.top, systemBars.right,
                    tecladoAberto ? teclado.bottom : 0);
            findViewById(R.id.barraNavegacao).setPadding(0, 0, 0, systemBars.bottom);
            return insets;
        });

        indicador = findViewById(R.id.indicadorAtivo);

        montarItem(R.id.navInicio, R.drawable.ic_nav_inicio, InicioFragment.class);
        montarItem(R.id.navChat, R.drawable.ic_nav_chat, AssistenteFragment.class);
        montarItem(R.id.navListas, R.drawable.ic_nav_listas, ListasFragment.class);
        montarItem(R.id.navPerfil, R.drawable.ic_nav_perfil, PerfilFragment.class);

        findViewById(R.id.linhaItens).addOnLayoutChangeListener(
                (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                    if (itemAtivo != null && (left != oldLeft || right != oldRight)) {
                        posicionarIndicador(false);
                    }
                });

        findViewById(R.id.btnScan).setOnClickListener(v -> iniciarScan());

        if (savedInstanceState != null) {
            int posicao = savedInstanceState.getInt(ESTADO_ITEM_ATIVO, 0);
            marcar(itens.get(Math.max(0, Math.min(posicao, itens.size() - 1))), false);
        } else {
            selecionar(findViewById(R.id.navInicio), new InicioFragment(), false);

            new SincronizacaoRepository(this).sincronizarEmSegundoPlano();
            new SincronizacaoListas(this).atualizarEmSegundoPlano(null);
            new SincronizacaoCurtidos(this).atualizarEmSegundoPlano(null);
            new FotoPerfilRepository(this).enviarEmSegundoPlano();
            Lembretes.reagendarTudo(this);
            ReenvioDeScanAgenda.agendarSePreciso(this);
            DecisoesAgenda.verificarAgora(this);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(ESTADO_ITEM_ATIVO, Math.max(0, itens.indexOf(itemAtivo)));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        selecionar(findViewById(R.id.navInicio), new InicioFragment(), true);
    }

    private void montarItem(int id, @DrawableRes int icone, Class<? extends Fragment> tela) {
        View item = findViewById(id);
        ((ImageView) item.findViewById(R.id.iconeNav)).setImageResource(icone);
        itens.add(item);

        item.setOnClickListener(v -> {
            try {
                selecionar(item, tela.newInstance(), true);
            } catch (IllegalAccessException | InstantiationException e) {
                Toast.makeText(this, R.string.em_breve, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void selecionar(View escolhido, Fragment fragment, boolean animar) {
        marcar(escolhido, animar);

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.containerConteudo, fragment)
                .commit();
    }

    private void marcar(View escolhido, boolean animar) {
        itemAtivo = escolhido;

        for (View item : itens) {
            boolean ativo = item == escolhido;
            ImageView icone = item.findViewById(R.id.iconeNav);

            icone.setColorFilter(ContextCompat.getColor(
                    this, ativo ? R.color.magenta_nav : R.color.cinza_nav));

            float escala = ativo ? ESCALA_ATIVO : 1f;
            if (animar) {
                icone.animate()
                        .scaleX(escala)
                        .scaleY(escala)
                        .setDuration(DURACAO_ANIMACAO)
                        .setInterpolator(new FastOutSlowInInterpolator())
                        .start();
            } else {
                icone.setScaleX(escala);
                icone.setScaleY(escala);
            }
        }

        posicionarIndicador(animar);
    }

    private void iniciarScan() {
        boolean onboardingCompleto = ScanTutorialState.isCompleted(this);
        startActivity(new Intent(
                this,
                onboardingCompleto ? ScanCameraFrontActivity.class : ScanTutorial1Activity.class
        ));
    }

    private void posicionarIndicador(boolean animar) {
        View linha = (View) itemAtivo.getParent();
        if (itemAtivo.getWidth() == 0) {
            return;
        }

        float larguraIndicador = getResources().getDimension(R.dimen.largura_indicador);
        float destino = linha.getX() + itemAtivo.getX() + (itemAtivo.getWidth() - larguraIndicador) / 2f;
        indicador.animate().cancel();
        if (animar) {
            indicador.animate()
                    .translationX(destino)
                    .setDuration(DURACAO_ANIMACAO)
                    .setInterpolator(new FastOutSlowInInterpolator())
                    .start();
        } else {
            indicador.setTranslationX(destino);
        }
    }
}
