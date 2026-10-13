package com.venussystem.venusmobile.view.dialog;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.content.ContextCompat;
import com.venussystem.venusmobile.R;

/** Scan feedback using the same palette, rounded card and buttons as the profile dialogs. */
public final class ScanStatusDialog {
    private ScanStatusDialog() { }

    public static AlertDialog create(Activity activity, String title, String message, boolean loading,
            String primaryLabel, Runnable primaryAction, Runnable exitAction) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(activity, 24), dp(activity, 24), dp(activity, 24), dp(activity, 20));
        card.setBackgroundResource(R.drawable.bg_modal_escolha);

        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(activity);
        icon.setImageResource(R.drawable.ic_scan);
        icon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(activity, R.color.roxo_venus)));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        header.addView(icon, new LinearLayout.LayoutParams(dp(activity, 28), dp(activity, 28)));
        TextView label = text(activity, "SCAN VENUS", 12, R.color.roxo_venus, true);
        label.setPadding(dp(activity, 10), 0, 0, 0);
        header.addView(label);
        card.addView(header);

        TextView heading = text(activity, title, 24, R.color.preto_texto, true);
        heading.setAccessibilityHeading(true);
        add(card, heading, 20);
        TextView body = text(activity, message, 15, R.color.cinza_descricao, false);
        body.setTag("scan_status_message");
        body.setLineSpacing(dp(activity, 3), 1f);
        add(card, body, 10);

        if (loading) {
            ProgressBar progress = new ProgressBar(activity);
            progress.setIndeterminateTintList(ColorStateList.valueOf(ContextCompat.getColor(activity, R.color.roxo_venus)));
            progress.setContentDescription("Aguardando resposta");
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(activity, 28), dp(activity, 28));
            params.gravity = Gravity.CENTER_HORIZONTAL;
            params.topMargin = dp(activity, 20);
            card.addView(progress, params);
        }

        TextView saved = text(activity, "Salvo no aparelho.",
                13, R.color.cinza_descricao, false);
        add(card, saved, 12);

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.addView(card);
        AlertDialog dialog = new AlertDialog.Builder(activity).setView(scroll).setCancelable(false).create();

        if (primaryAction != null) {
            AppCompatButton primary = button(activity, primaryLabel, true);
            primary.setOnClickListener(v -> { v.setEnabled(false); dialog.dismiss(); primaryAction.run(); });
            add(card, primary, 20);
        }
        AppCompatButton exit = button(activity, "Voltar", false);
        exit.setOnClickListener(v -> { v.setEnabled(false); dialog.dismiss(); exitAction.run(); });
        add(card, exit, primaryAction == null ? 20 : 8);

        dialog.setOnShowListener(ignored -> {
            if (dialog.getWindow() == null) return;
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int available = activity.getResources().getDisplayMetrics().widthPixels - dp(activity, 40);
            dialog.getWindow().setLayout(Math.min(available, dp(activity, 420)), ViewGroup.LayoutParams.WRAP_CONTENT);
        });
        return dialog;
    }

    public static void updateMessage(AlertDialog dialog, String message) {
        if (dialog.getWindow() == null || !dialog.isShowing()) return;
        TextView body = dialog.getWindow().getDecorView().findViewWithTag("scan_status_message");
        if (body != null) body.setText(message);
    }

    private static TextView text(Activity activity, String value, int size, int color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(ContextCompat.getColor(activity, color));
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        return view;
    }
    private static AppCompatButton button(Activity activity, String label, boolean primary) {
        AppCompatButton button = new AppCompatButton(activity);
        button.setText(label);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setMinHeight(dp(activity, 52));
        button.setPadding(dp(activity, 12), dp(activity, 12), dp(activity, 12), dp(activity, 12));
        button.setBackgroundResource(primary ? R.drawable.bg_botao_gradiente : android.R.color.transparent);
        button.setSupportBackgroundTintList(null);
        button.setTextColor(ContextCompat.getColor(activity, primary ? R.color.white : R.color.roxo_venus));
        return button;
    }
    private static void add(LinearLayout parent, View child, int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = Math.round(margin * parent.getResources().getDisplayMetrics().density);
        parent.addView(child, params);
    }
    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
