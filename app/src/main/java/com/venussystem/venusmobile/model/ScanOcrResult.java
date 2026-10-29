package com.venussystem.venusmobile.model;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ScanOcrResult {

    private final String fullText;
    private final List<String> lines;
    private final List<ScanOcrToken> tokens;
    private final List<ScanOcrAttempt> attempts;

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

    public ScanOcrResult(
            @NonNull String fullText,
            @NonNull List<String> lines,
            @NonNull List<ScanOcrToken> tokens
    ) {
        this(fullText, lines, tokens, Collections.emptyList());
    }

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

    @NonNull
    public List<ScanOcrAttempt> getAttempts() {
        return Collections.unmodifiableList(attempts);
    }

    @NonNull
    public String getSelectedVariant() {
        for (ScanOcrAttempt attempt : attempts) {
            if (attempt.isSelected()) {
                return attempt.getVariant();
            }
        }
        return "UNKNOWN";
    }

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
