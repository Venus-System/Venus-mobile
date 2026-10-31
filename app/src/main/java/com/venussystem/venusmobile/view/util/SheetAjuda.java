package com.venussystem.venusmobile.view.util;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.venussystem.venusmobile.R;

public final class SheetAjuda {

    public interface AoMontar {
        void montou(View conteudo, BottomSheetDialog sheet);
    }

    private SheetAjuda() {
    }

    public static void mostrar(Context context, @LayoutRes int layout, @Nullable AoMontar aoMontar) {
        BottomSheetDialog sheet = new BottomSheetDialog(context);
        View conteudo = LayoutInflater.from(context).inflate(layout, null);
        sheet.setContentView(conteudo);

        conteudo.findViewById(R.id.btnFecharSheet).setOnClickListener(x -> sheet.dismiss());
        conteudo.findViewById(R.id.btnEntendi).setOnClickListener(x -> sheet.dismiss());

        if (aoMontar != null) {
            aoMontar.montou(conteudo, sheet);
        }

        sheet.show();
    }
}
