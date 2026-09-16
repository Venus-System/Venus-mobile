package com.venussystem.venusmobile.view;

import android.content.Context;
import android.content.SharedPreferences;

import com.venussystem.venusmobile.repository.AutenticacaoRepository;
import com.venussystem.venusmobile.model.Usuario;

/**
 * Persiste o onboarding do Scan por usuário autenticado, e não por dispositivo.
 * Assim, cada conta vê os tutoriais uma única vez de forma independente.
 */
public final class ScanTutorialState {
    public static final String PREFS = "scan_preferences_v2";
    private static final String PREFIX = "scan_onboarding_completo_";

    private ScanTutorialState() {}

    private static String userKey(Context context) {
        Usuario usuario = new AutenticacaoRepository().usuarioLogado();
        String uid = usuario != null ? usuario.getUid() : null;
        if (uid == null || uid.trim().isEmpty()) {
            return "anonymous";
        }
        return uid.trim();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String completedKey(Context context) {
        return PREFIX + userKey(context);
    }

    public static boolean isCompleted(Context context) {
        return prefs(context).getBoolean(completedKey(context), false);
    }

    public static void markCompleted(Context context) {
        prefs(context).edit().putBoolean(completedKey(context), true).apply();
    }

    public static String debugCurrentUserKey(Context context) {
        return completedKey(context);
    }
}
