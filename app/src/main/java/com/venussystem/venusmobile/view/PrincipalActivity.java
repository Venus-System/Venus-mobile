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
import com.venussystem.venusmobile.repository.AutenticacaoRepository;
import com.venussystem.venusmobile.repository.PerfilRepository;
import com.venussystem.venusmobile.view.fragment.BuscaFragment;
import com.venussystem.venusmobile.view.fragment.HistoricoFragment;
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

            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            findViewById(R.id.barraNavegacao).setPadding(0, 0, 0, systemBars.bottom);
            return insets;
        });

        indicador = findViewById(R.id.indicadorAtivo);

        montarItem(R.id.navInicio, R.drawable.ic_nav_inicio, HistoricoFragment.class);
        montarItem(R.id.navBusca, R.drawable.ic_nav_busca, BuscaFragment.class);
        montarItem(R.id.navListas, R.drawable.ic_nav_listas, ListasFragment.class);
        montarItem(R.id.navPerfil, R.drawable.ic_nav_perfil, PerfilFragment.class);

        findViewById(R.id.btnAssistente).setOnClickListener(v ->
                startActivity(new Intent(this, AssistenteActivity.class)));

        findViewById(R.id.btnReiniciarTeste).setOnClickListener(v -> reiniciarTeste());

        if (savedInstanceState == null) {
            selecionar(findViewById(R.id.navBusca), new BuscaFragment(), false);
        }
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

    private void reiniciarTeste() {
        new PerfilRepository(this).limpar();
        new AutenticacaoRepository().sair();

        Intent intent = new Intent(this, LoginActivity.class);

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void moverIndicador(View item, boolean animar) {
        indicador.post(() -> {
            float destino = item.getX() + (item.getWidth() - indicador.getWidth()) / 2f;
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
