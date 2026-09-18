package com.venussystem.venusmobile.view;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.view.fragment.BuscaFragment;

/**
 * Hospeda a mesma tela de busca da aba Buscar, mas em modo escolha: tocar num
 * produto nao abre o detalhe dele, devolve o id escolhido pra quem chamou
 * (ver DetalheListaActivity) via activity result.
 */
public class EscolherProdutoActivity extends AppCompatActivity {

    public static final String EXTRA_PRODUTO_ID = "produto_id";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetsSistema.aplicar(this, R.layout.activity_escolher_produto);

        findViewById(R.id.btnVoltar).setOnClickListener(v -> finish());

        getSupportFragmentManager().setFragmentResultListener(
                BuscaFragment.RESULTADO_ESCOLHA_PRODUTO, this, (chave, resultado) -> {
                    long produtoId = resultado.getLong(BuscaFragment.EXTRA_PRODUTO_ID, -1);
                    Intent dados = new Intent();
                    dados.putExtra(EXTRA_PRODUTO_ID, produtoId);
                    setResult(RESULT_OK, dados);
                    finish();
                });

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.containerEscolherProduto, BuscaFragment.paraEscolherProduto())
                    .commit();
        }
    }
}
