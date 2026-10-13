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
}
