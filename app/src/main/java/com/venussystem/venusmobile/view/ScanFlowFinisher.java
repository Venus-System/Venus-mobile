package com.venussystem.venusmobile.view;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

/** Finaliza o fluxo de análise e grava o onboarding para o usuário atual. */
public final class ScanFlowFinisher {
    private ScanFlowFinisher() {}

    public static void finalizar(Activity activity) {
        ScanTutorialState.markCompleted(activity);

        Toast.makeText(
                activity,
                "Produto enviado para análise!",
                Toast.LENGTH_LONG
        ).show();

        Intent intent = new Intent(activity, PrincipalActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        activity.startActivity(intent);
        activity.finish();
    }
}
