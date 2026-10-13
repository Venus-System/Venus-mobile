package com.venussystem.venusmobile.view;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.venussystem.venusmobile.model.Usuario;
import com.venussystem.venusmobile.repository.AutenticacaoRepository;

/**
 * Controla individualmente quais telas de tutorial do Scan o usuário já viu.
 *
 * Importante: "tutorial visto" é diferente de "fluxo concluído".
 * Cada passo é persistido separadamente para permitir:
 * - Passo 1 apenas uma vez;
 * - Passo 2 apenas uma vez;
 * - Passo 3 apenas uma vez;
 * - a tela "Produto não cadastrado" continuar aparecendo sempre que necessária.
 */
public final class ScanTutorialState {

    public static final String PREFS = "scan_preferences_v3";

    private static final String PREFIX = "scan_onboarding_";
    private static final String KEY_TUTORIAL_1 = "tutorial_1_visto_";
    private static final String KEY_TUTORIAL_2 = "tutorial_2_visto_";
    private static final String KEY_TUTORIAL_3 = "tutorial_3_visto_";
    private static final String KEY_COMPLETED = "fluxo_completo_";

    /** Chave antiga, mantida somente para migração. */
    private static final String LEGACY_PREFS = "scan_preferences_v2";
    private static final String LEGACY_PREFIX = "scan_onboarding_completo_";

    private ScanTutorialState() {
    }

    @NonNull
    private static String userKey(Context context) {
        try {
            Usuario usuario = new AutenticacaoRepository().usuarioLogado();
            String uid = usuario != null ? usuario.getUid() : null;

            if (uid != null && !uid.trim().isEmpty()) {
                return uid.trim();
            }
        } catch (Exception ignored) {
            // Mantemos o fluxo funcionando mesmo se a autenticação estiver indisponível.
        }

        return "anonymous";
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
        );
    }

    private static boolean legadoConcluido(Context context) {
        try {
            SharedPreferences legacy =
                    context.getSharedPreferences(
                            LEGACY_PREFS,
                            Context.MODE_PRIVATE
                    );

            return legacy.getBoolean(
                    LEGACY_PREFIX + userKey(context),
                    false
            );
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String key(String suffix, Context context) {
        return PREFIX + suffix + userKey(context);
    }

    public static boolean isTutorial1Seen(Context context) {
        return prefs(context).getBoolean(
                key(KEY_TUTORIAL_1, context),
                false
        ) || legadoConcluido(context);
    }

    public static void markTutorial1Seen(Context context) {
        prefs(context)
                .edit()
                .putBoolean(key(KEY_TUTORIAL_1, context), true)
                .apply();
    }

    public static boolean isTutorial2Seen(Context context) {
        return prefs(context).getBoolean(
                key(KEY_TUTORIAL_2, context),
                false
        ) || legadoConcluido(context);
    }

    public static void markTutorial2Seen(Context context) {
        prefs(context)
                .edit()
                .putBoolean(key(KEY_TUTORIAL_2, context), true)
                .apply();
    }

    public static boolean isTutorial3Seen(Context context) {
        return prefs(context).getBoolean(
                key(KEY_TUTORIAL_3, context),
                false
        ) || legadoConcluido(context);
    }

    public static void markTutorial3Seen(Context context) {
        prefs(context)
                .edit()
                .putBoolean(key(KEY_TUTORIAL_3, context), true)
                .apply();
    }

    public static boolean areAllTutorialsSeen(Context context) {
        return isTutorial1Seen(context)
                && isTutorial2Seen(context)
                && isTutorial3Seen(context);
    }

    /** Compatibilidade com classes antigas do projeto. */
    public static boolean isCompleted(Context context) {
        return prefs(context).getBoolean(
                key(KEY_COMPLETED, context),
                false
        ) || areAllTutorialsSeen(context);
    }

    /** Compatibilidade com classes antigas do projeto. */
    public static void markCompleted(Context context) {
        prefs(context)
                .edit()
                .putBoolean(key(KEY_TUTORIAL_1, context), true)
                .putBoolean(key(KEY_TUTORIAL_2, context), true)
                .putBoolean(key(KEY_TUTORIAL_3, context), true)
                .putBoolean(key(KEY_COMPLETED, context), true)
                .apply();
    }

    @NonNull
    public static String debugCurrentUserKey(Context context) {
        return userKey(context);
    }
}
