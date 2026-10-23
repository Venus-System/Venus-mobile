package com.venussystem.venusmobile.model;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Resultado bruto do OCR.
 *
 * V7 adiciona os tokens espaciais do ML Kit sem remover a API textual
 * existente. Assim, os parsers antigos continuam funcionando quando o
 * resultado for criado somente com fullText + lines.
 */
public class ScanOcrResult {

    private final String fullText;
    private final List<String> lines;
    private final List<ScanOcrToken> tokens;
    private final List<ScanOcrAttempt> attempts;

    /**
     * Construtor legado: mantém compatibilidade com o fluxo V6.x.
     */
    public ScanOcrResult(
            @NonNull String fullText,
            @NonNull List<String> lines
    ) {
        this(
                fullText,
                lines,
                Collections.emptyList(),
                Collections.emptyList()
        );
    }

    /**
     * Construtor V7 com informação espacial.
     */
    public ScanOcrResult(
            @NonNull String fullText,
            @NonNull List<String> lines,
            @NonNull List<ScanOcrToken> tokens
    ) {
        this(fullText, lines, tokens, Collections.emptyList());
    }

    /** V8 result with information about every local OCR pass. */
    public ScanOcrResult(
            @NonNull String fullText,
            @NonNull List<String> lines,
            @NonNull List<ScanOcrToken> tokens,
            @NonNull List<ScanOcrAttempt> attempts
    ) {
        this.fullText = fullText;
        this.lines = new ArrayList<>(lines);
        this.tokens = new ArrayList<>(tokens);
        this.attempts = new ArrayList<>(attempts);
    }

    @NonNull
    public String getFullText() {
        return fullText;
    }

    @NonNull
    public List<String> getLines() {
        return Collections.unmodifiableList(lines);
    }

    /**
     * Retorna os elementos do OCR com posição espacial, quando disponíveis.
     */
    @NonNull
    public List<ScanOcrToken> getTokens() {
        return Collections.unmodifiableList(tokens);
    }

    public boolean hasSpatialData() {
        return !tokens.isEmpty();
    }

    public int getTokenCount() {
        return tokens.size();
    }

    /** Metadata only; the text of discarded passes is never retained. */
    @NonNull
    public List<ScanOcrAttempt> getAttempts() {
        return Collections.unmodifiableList(attempts);
    }

    /** Returns only the selected pass identifier; never exposes OCR text. */
    @NonNull
    public String getSelectedVariant() {
        for (ScanOcrAttempt attempt : attempts) {
            if (attempt.isSelected()) {
                return attempt.getVariant();
            }
        }
        return "UNKNOWN";
    }

    /** Spatial tokens are safe only when the selected pass kept source geometry. */
    public boolean hasSelectedSpatialGeometry() {
        for (ScanOcrAttempt attempt : attempts) {
            if (attempt.isSelected()) return attempt.preservesSourceGeometry();
        }
        return true;
    }

    public boolean isEmpty() {
        return fullText.trim().isEmpty();
    }
}
