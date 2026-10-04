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
        this.fullText = fullText;
        this.lines = new ArrayList<>(lines);
        this.tokens = new ArrayList<>(tokens);
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

    public boolean isEmpty() {
        return fullText.trim().isEmpty();
    }
}
