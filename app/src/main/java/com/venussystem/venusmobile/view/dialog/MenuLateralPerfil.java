package com.venussystem.venusmobile.view.dialog;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.sidesheet.SideSheetDialog;
import com.venussystem.venusmobile.R;

/**
 * Menu lateral do perfil, aberto pelo icone de tres linhas no topo. Desliza
 * da direita por cima do app (SideSheetDialog do Material): o resto da tela
 * escurece, e tocar fora ou apertar voltar fecha.
 *
 * Os itens ficam embaixo do nome ("Alterar foto de perfil", "Produtos em
 * analise"), e "Voltar ao login" fica sempre no fim.
 */
public final class MenuLateralPerfil {

    private MenuLateralPerfil() {
    }

    /**
     * As acoes sao chamadas depois que o menu fecha; quem abriu decide o que
     * cada uma faz.
     */
    public static void mostrar(Context contexto, CharSequence nome, CharSequence email,
                               Runnable aoAlterarFoto, Runnable aoAbrirProdutosEmAnalise,
                               Runnable aoVoltarAoLogin) {
        // Largura, fundo branco e cantos arredondados: ver
        // ThemeOverlay.VenusMobile.MenuLateral em styles.xml.
        SideSheetDialog menu = new SideSheetDialog(contexto, R.style.ThemeOverlay_VenusMobile_MenuLateral);
        View conteudo = LayoutInflater.from(contexto).inflate(R.layout.dialog_menu_perfil, null);

        ((TextView) conteudo.findViewById(R.id.textNomeMenu)).setText(nome);
        ((TextView) conteudo.findViewById(R.id.textEmailMenu)).setText(email);

        conteudo.findViewById(R.id.btnFecharMenu).setOnClickListener(v -> menu.dismiss());
        conteudo.findViewById(R.id.btnAlterarFoto).setOnClickListener(v -> {
            menu.dismiss();
            aoAlterarFoto.run();
        });
        conteudo.findViewById(R.id.btnProdutosEmAnalise).setOnClickListener(v -> {
            menu.dismiss();
            aoAbrirProdutosEmAnalise.run();
        });
        conteudo.findViewById(R.id.btnVoltarAoLogin).setOnClickListener(v -> {
            menu.dismiss();
            aoVoltarAoLogin.run();
        });

        respeitarBarrasDoSistema(conteudo);
        menu.setContentView(conteudo);
        menu.show();
    }

    /**
     * O app desenha atras da barra de status e da de navegacao (EdgeToEdge).
     * Sem isto, o titulo do menu ficaria embaixo do relogio e o "Voltar ao
     * menu" embaixo dos botoes do sistema. Soma ao espaco que o layout ja tem.
     */
    private static void respeitarBarrasDoSistema(View conteudo) {
        int topo = conteudo.getPaddingTop();
        int base = conteudo.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(conteudo, (v, insets) -> {
            Insets barras = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), topo + barras.top, v.getPaddingRight(),
                    base + barras.bottom);
            return insets;
        });
    }
}
