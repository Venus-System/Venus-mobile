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
import com.venussystem.venusmobile.repository.FotoPerfilRepository;
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

    private final List<View> itens = new ArrayList<>();
    private View indicador;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_principal);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets teclado = insets.getInsets(WindowInsetsCompat.Type.ime());
            boolean tecladoAberto = teclado.bottom > 0;

            // Com o teclado aberto (o campo de mensagem do chat) o rodape sai da
            // frente e a tela acaba logo acima do teclado: assim o campo
            // continua visivel, em vez de espremido entre o teclado e o rodape.
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
        montarItem(R.id.navChat, R.drawable.ic_chat, AssistenteFragment.class);
        montarItem(R.id.navListas, R.drawable.ic_nav_listas, ListasFragment.class);
        montarItem(R.id.navPerfil, R.drawable.ic_nav_perfil, PerfilFragment.class);

        findViewById(R.id.btnScan).setOnClickListener(v -> iniciarScan());

        if (savedInstanceState == null) {
            selecionar(findViewById(R.id.navInicio), new InicioFragment(), false);

            // Toda entrada no app passa por aqui (fim do questionario, login ou
            // app reaberto): e o ponto para cadastrar a pessoa na API e mandar o
            // que ficou pendente. Roda em segundo plano, sem segurar a tela.
            new SincronizacaoRepository(this).sincronizarEmSegundoPlano();
            // As listas vao depois, na mesma fila: sobe o que ficou pendente e
            // vem o que mudou no servidor. Num celular novo ou depois de
            // reinstalar, e aqui que as listas que a pessoa ja tinha voltam.
            new SincronizacaoListas(this).atualizarEmSegundoPlano(null);
            // Foto de perfil que ficou sem enviar (sem rede, API dormindo).
            new FotoPerfilRepository(this).enviarEmSegundoPlano();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        // O scan retorna para a mesma PrincipalActivity. Reabre a casinha, para
        // a pessoa voltar ao ponto de partida; o produto enviado aparece no
        // historico da tela de busca.
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

        moverIndicador(escolhido, animar);

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.containerConteudo, fragment)
                .commit();
    }

    private void iniciarScan() {
        boolean onboardingCompleto = ScanTutorialState.isCompleted(this);
        startActivity(new Intent(
                this,
                onboardingCompleto ? ScanCameraFrontActivity.class : ScanTutorial1Activity.class
        ));
    }

    private void moverIndicador(View item, boolean animar) {
        indicador.post(() -> {
            // No tablet a linha de itens fica centralizada dentro da barra, entao o
            // deslocamento dela entra na conta (no celular ele e zero).
            View linha = (View) item.getParent();
            float destino = linha.getX() + item.getX() + (item.getWidth() - indicador.getWidth()) / 2f;
            if (animar) {
                indicador.animate()
                        .translationX(destino)
                        .setDuration(DURACAO_ANIMACAO)
                        .setInterpolator(new FastOutSlowInInterpolator())
                        .start();
            } else {
                indicador.setTranslationX(destino);
            }
        });
    }
}
