package com.venussystem.venusmobile.view;

import android.os.Bundle;
import android.view.View;
import android.widget.CompoundButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.CategoriaContagem;
import com.venussystem.venusmobile.model.FaixaNota;
import com.venussystem.venusmobile.model.FiltroProdutos;
import com.venussystem.venusmobile.model.MarcaFiltro;
import com.venussystem.venusmobile.viewmodel.FiltroViewModel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * O filtro do catalogo, em tela propria e sem o rodape. Aberto pela Home e
 * pela busca, e o mesmo nas duas: Aplicar vale para as duas, e a seta de voltar
 * descarta o que foi marcado.
 */
public class FiltroActivity extends AppCompatActivity {

    private FiltroViewModel viewModel;

    private Chip chipBoa;
    private Chip chipMedia;
    private Chip chipRuim;
    private SwitchCompat switchBrasileiras;
    private View secaoCategorias;
    private View secaoMarcas;
    private ChipGroup grupoCategorias;
    private ChipGroup grupoMarcas;

    private final Map<Long, Chip> chipsCategoria = new HashMap<>();
    private final Map<Long, Chip> chipsMarca = new HashMap<>();

    // Marcar um chip pelo codigo (para refletir o rascunho) dispara o mesmo
    // listener de quando a pessoa toca. Sem esta trava, um mudaria o outro.
    private boolean sincronizando;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetsSistema.aplicar(this, R.layout.activity_filtro);

        viewModel = new ViewModelProvider(this).get(FiltroViewModel.class);

        chipBoa = findViewById(R.id.chipNotaBoa);
        chipMedia = findViewById(R.id.chipNotaMedia);
        chipRuim = findViewById(R.id.chipNotaRuim);
        switchBrasileiras = findViewById(R.id.switchBrasileiras);
        secaoCategorias = findViewById(R.id.secaoCategorias);
        secaoMarcas = findViewById(R.id.secaoMarcas);
        grupoCategorias = findViewById(R.id.grupoCategorias);
        grupoMarcas = findViewById(R.id.grupoMarcas);

        findViewById(R.id.btnVoltar).setOnClickListener(v -> finish());
        findViewById(R.id.btnLimpar).setOnClickListener(v -> viewModel.limpar());
        findViewById(R.id.btnAplicar).setOnClickListener(v -> {
            viewModel.aplicar();
            finish();
        });

        prepararFaixa(chipBoa, FaixaNota.BOA);
        prepararFaixa(chipMedia, FaixaNota.MEDIA);
        prepararFaixa(chipRuim, FaixaNota.RUIM);
        switchBrasileiras.setOnCheckedChangeListener((botao, marcado) -> {
            if (!sincronizando) {
                viewModel.definirSoBrasileiras(marcado);
            }
        });

        observarViewModel();
    }

    private void prepararFaixa(Chip chip, FaixaNota faixa) {
        chip.setOnCheckedChangeListener((botao, marcado) -> {
            if (!sincronizando) {
                viewModel.alternarFaixa(faixa, marcado);
            }
        });
    }

    private void observarViewModel() {
        viewModel.getCategorias().observe(this, this::montarCategorias);
        viewModel.getMarcas().observe(this, this::montarMarcas);
        viewModel.getRascunho().observe(this, rascunho -> sincronizar());
        viewModel.getTemMarcaBrasileira().observe(this, tem ->
                switchBrasileiras.setVisibility(Boolean.TRUE.equals(tem) ? View.VISIBLE : View.GONE));

        // Os grupos de categoria e marca so existem depois que o catalogo chega.
        viewModel.getCarregando().observe(this, ocupado -> findViewById(R.id.carregando)
                .setVisibility(Boolean.TRUE.equals(ocupado) && !viewModel.temCatalogo()
                        ? View.VISIBLE : View.GONE));
    }

    private void montarCategorias(List<CategoriaContagem> categorias) {
        chipsCategoria.clear();
        grupoCategorias.removeAllViews();
        secaoCategorias.setVisibility(
                categorias == null || categorias.isEmpty() ? View.GONE : View.VISIBLE);
        if (categorias == null) {
            return;
        }

        for (CategoriaContagem categoria : categorias) {
            long id = categoria.getId();
            Chip chip = novoChip(grupoCategorias, categoria.getNome());
            chip.setOnCheckedChangeListener((botao, marcado) -> {
                if (!sincronizando) {
                    viewModel.alternarCategoria(id, marcado);
                }
            });
            chipsCategoria.put(id, chip);
        }
        sincronizar();
    }

    private void montarMarcas(List<MarcaFiltro> marcas) {
        chipsMarca.clear();
        grupoMarcas.removeAllViews();
        secaoMarcas.setVisibility(marcas == null || marcas.isEmpty() ? View.GONE : View.VISIBLE);
        if (marcas == null) {
            return;
        }

        for (MarcaFiltro marca : marcas) {
            long id = marca.getId();
            Chip chip = novoChip(grupoMarcas, marca.getNome());
            chip.setOnCheckedChangeListener((botao, marcado) -> {
                if (!sincronizando) {
                    viewModel.alternarMarca(id, marcado);
                }
            });
            chipsMarca.put(id, chip);
        }
        sincronizar();
    }

    private Chip novoChip(ChipGroup grupo, String texto) {
        Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_chip_categoria, grupo, false);
        chip.setId(View.generateViewId());
        chip.setText(texto);
        grupo.addView(chip);
        return chip;
    }

    /** Reflete o rascunho nos chips e no interruptor. */
    private void sincronizar() {
        FiltroProdutos rascunho = viewModel.getRascunho().getValue();
        if (rascunho == null) {
            return;
        }

        sincronizando = true;
        marcar(chipBoa, rascunho.getFaixas().contains(FaixaNota.BOA));
        marcar(chipMedia, rascunho.getFaixas().contains(FaixaNota.MEDIA));
        marcar(chipRuim, rascunho.getFaixas().contains(FaixaNota.RUIM));
        marcar(switchBrasileiras, rascunho.isSoBrasileiras());
        for (Map.Entry<Long, Chip> entrada : chipsCategoria.entrySet()) {
            marcar(entrada.getValue(), rascunho.getCategorias().contains(entrada.getKey()));
        }
        for (Map.Entry<Long, Chip> entrada : chipsMarca.entrySet()) {
            marcar(entrada.getValue(), rascunho.getMarcas().contains(entrada.getKey()));
        }
        sincronizando = false;
    }

    private static void marcar(CompoundButton botao, boolean marcado) {
        if (botao.isChecked() != marcado) {
            botao.setChecked(marcado);
        }
    }
}
