package com.venussystem.venusmobile.model;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ScanOcrResult {

    private final String fullText;
    private final List<String> lines;

    public ScanOcrResult(
            @NonNull String fullText,
            @NonNull List<String> lines
    ) {
        this.fullText = fullText;
        this.lines = new ArrayList<>(lines);
    }

    @NonNull
    public String getFullText() {
        return fullText;
    }

    @NonNull
    public List<String> getLines() {
        return Collections.unmodifiableList(lines);
    }

    public boolean isEmpty() {
        return fullText.trim().isEmpty();
    }
}