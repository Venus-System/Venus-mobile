package com.venussystem.venusmobile.view;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.Produto;
import com.venussystem.venusmobile.view.fragment.AssistenteFragment;

/**
 * O chat aberto a partir do detalhe de um produto, ja perguntando sobre ele. A
 * conversa e a mesma da aba Chat (ela fica salva por conta); esta tela so
 * hospeda o fragment, com a seta de voltar que leva de volta ao produto.
 */
public class AssistenteActivity extends AppCompatActivity {

    /** Abre o chat falando de um produto. */
    public static Intent paraProduto(Context context, @Nullable Produto produto) {
        Intent intent = new Intent(context, AssistenteActivity.class);
        intent.putExtras(AssistenteFragment.extrasDoProduto(produto));
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetsSistema.aplicarComTeclado(this, R.layout.activity_assistente);

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.containerAssistente,
                            AssistenteFragment.doProduto(getIntent().getExtras()))
                    .commit();
        }
    }
}
