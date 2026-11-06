package com.venussystem.venusmobile.view.util;

import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Renderizador Markdown pequeno e seguro para as bolhas do assistente. */
public final class MarkdownRenderer {
    private static final Pattern LINK = Pattern.compile(
            "\\[([^\\]]{0,200})\\]\\((https?://[^\\s)<>]{1,1000})\\)");
    private static final Pattern CODE = Pattern.compile("(`)([^`\\n]{0,1000})\\1");
    private static final Pattern BOLD = Pattern.compile("(\\*\\*|__)(?=\\S)(.+?)(?<=\\S)\\1");
    private static final Pattern ITALIC = Pattern.compile("(?<!\\w)(\\*|_)(?=\\S)(.+?)(?<=\\S)\\1");

    private MarkdownRenderer() {
    }

    @NonNull
    public static Spanned render(String markdown) {
        String texto = markdown == null ? "" : markdown.replace("\r\n", "\n");
        String[] linhas = texto.split("\\n", -1);
        StringBuilder html = new StringBuilder();
        boolean emCodigo = false;

        for (int indice = 0; indice < linhas.length; indice++) {
            String linha = linhas[indice];
            if (linha.trim().startsWith("```") && linha.trim().length() <= 80) {
                if (emCodigo) {
                    html.append("</code></pre>");
                } else {
                    html.append("<pre><code>");
                }
                emCodigo = !emCodigo;
                continue;
            }
            if (emCodigo) {
                html.append(TextUtils.htmlEncode(linha)).append("\n");
                continue;
            }

            String processada = TextUtils.htmlEncode(linha);
            processada = processada.replaceFirst("^#{1,6}\\s+", "");
            boolean titulo = linha.matches("^#{1,6}\\s+.+$");
            boolean lista = linha.matches("^\\s*[-+*]\\s+.+$");
            if (lista) {
                processada = processada.replaceFirst("^\\s*[-+*]\\s+", "• ");
            } else {
                processada = processada.replaceFirst("^\\s*(\\d+)\\.\\s+", "$1. ");
            }
            processada = links(processada);
            processada = substituicoes(processada, CODE, "<code>$2</code>");
            processada = substituicoes(processada, BOLD, "<b>$2</b>");
            processada = substituicoes(processada, ITALIC, "<i>$2</i>");
            html.append(titulo ? "<big><b>" + processada + "</b></big>" : processada);
            if (indice < linhas.length - 1) {
                html.append("<br>");
            }
        }

        if (emCodigo) {
            html.append("</code></pre>");
        }
        return Html.fromHtml(html.toString(), Html.FROM_HTML_MODE_LEGACY);
    }

    @NonNull
    public static String textoAcessivel(String markdown) {
        return render(markdown).toString().trim();
    }

    private static String links(String texto) {
        Matcher matcher = LINK.matcher(texto);
        StringBuffer resultado = new StringBuffer();
        while (matcher.find()) {
            String link = "<a href=\"" + matcher.group(2) + "\">"
                    + matcher.group(1) + "</a>";
            matcher.appendReplacement(resultado, Matcher.quoteReplacement(link));
        }
        matcher.appendTail(resultado);
        return resultado.toString();
    }

    private static String substituicoes(String texto, Pattern padrao, String substituicao) {
        Matcher matcher = padrao.matcher(texto);
        StringBuffer resultado = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(resultado, Matcher.quoteReplacement(
                    substituicao.replace("$2", matcher.group(2))));
        }
        matcher.appendTail(resultado);
        return resultado.toString();
    }
}
