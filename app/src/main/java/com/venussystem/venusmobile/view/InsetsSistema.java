package com.venussystem.venusmobile.view;

import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.annotation.LayoutRes;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.venussystem.venusmobile.R;

public class InsetsSistema {
    private InsetsSistema() {
    }

    public static void aplicar(ComponentActivity activity, @LayoutRes int layout) {
        EdgeToEdge.enable(activity);
        activity.setContentView(layout);
        ViewCompat.setOnApplyWindowInsetsListener(activity.findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    /**
     * Como aplicar(), mas para telas com campo de texto: quando o teclado esta
     * aberto, a margem de baixo passa a ser a altura dele, e nao a da barra de
     * navegacao - senao o teclado cobriria o que fica embaixo.
     */
    public static void aplicarComTeclado(ComponentActivity activity, @LayoutRes int layout) {
        EdgeToEdge.enable(activity);
        activity.setContentView(layout);
        ViewCompat.setOnApplyWindowInsetsListener(activity.findViewById(R.id.main), (v, insets) -> {
            Insets barras = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets teclado = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(barras.left, barras.top, barras.right,
                    Math.max(barras.bottom, teclado.bottom));
            return insets;
        });
    }
}
