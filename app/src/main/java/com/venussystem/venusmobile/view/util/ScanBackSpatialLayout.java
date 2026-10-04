package com.venussystem.venusmobile.view.util;

import android.util.Log;

import com.venussystem.venusmobile.model.ScanOcrToken;
import com.venussystem.venusmobile.domain.scan.ScanIngredientPolicy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Reconstructs visual rows before section detection, without changing OCR spelling. */
final class ScanBackSpatialLayout {
    private ScanBackSpatialLayout() { }
    private static final double MAX_TEXT_ROTATION = 25.0;
    private static final double MAX_PERSPECTIVE_SPREAD = 20.0;

    // Empty means uncertain/incomplete geometry: the caller must use the source text.
    static List<List<ScanOcrToken>> rows(List<String> source, List<ScanOcrToken> tokens) {
        if (tokens == null || tokens.isEmpty()) return fallback("NO_TOKENS");
        List<String> nonEmpty = new ArrayList<>();
        for (String line : source) {
            if (line != null && !line.trim().isEmpty()) nonEmpty.add(line);
        }
        Map<Integer, List<ScanOcrToken>> original = new TreeMap<>();
        List<Double> angles = new ArrayList<>();
        List<Integer> heights = new ArrayList<>();
        for (ScanOcrToken token : tokens) {
            if (token == null || token.getBoundingBox() == null
                    || token.getHeight() <= 0 || token.getWidth() <= 0)
                return fallback("MISSING_OR_INVALID_BOX");
            if (token.getLineIndex() < 0 || token.getLineIndex() >= nonEmpty.size())
                return fallback("LINE_INDEX_OUT_OF_RANGE line=" + token.getLineIndex());
            if (!Float.isFinite(token.getRotation()))
                return fallback("ROTATION line=" + token.getLineIndex() + " angle=" + token.getRotation());
            // Recognition confidence measures spelling, not the validity of a box.
            // Keep every token; geometry is checked independently below.
            if (token.getConfidence() > 0 && token.getConfidence() < 0.5f)
                Log.d("VENUS_BACK_PARSE", "BACK_TEXT_LOW_CONFIDENCE line="
                        + token.getLineIndex() + " value=" + token.getConfidence());
            original.computeIfAbsent(token.getLineIndex(), k -> new ArrayList<>()).add(token);
        }
        // Never replace whole OCR lines with a partial subset of their elements.
        if (original.size() != nonEmpty.size()) return fallback("INCOMPLETE_LINE_COVERAGE");
        for (Map.Entry<Integer, List<ScanOcrToken>> entry : original.entrySet()) {
            entry.getValue().sort(Comparator.comparingInt(ScanOcrToken::getElementIndex));
            if (!compact(join(entry.getValue())).equals(compact(nonEmpty.get(entry.getKey())))) {
                return fallback("TEXT_TOKEN_MISMATCH line=" + entry.getKey());
            }
        }
        List<ScanOcrToken> primary = new ArrayList<>();
        List<List<ScanOcrToken>> peripheral = new ArrayList<>();
        for (List<ScanOcrToken> line : original.values()) {
            boolean horizontal = line.stream().allMatch(t -> Math.abs(t.getRotation()) <= MAX_TEXT_ROTATION);
            if (horizontal) {
                primary.addAll(line);
            } else {
                // Separate complete lines only. Never lose an ingredient by filtering tokens.
                float orientation = line.get(0).getRotation();
                String text = ScanIngredientPolicy.normalize(join(line));
                boolean code = text.matches("[A-Z0-9 ]+") && text.matches(".*[0-9].*")
                        && ScanIngredientPolicy.isPackagingTailLine(text);
                boolean vertical = Math.abs(Math.abs(orientation) - 90) <= 12
                        && line.stream().allMatch(t -> Math.abs(t.getRotation() - orientation) <= 12);
                if (!code || !vertical) return fallback("AMBIGUOUS_ORIENTATION line="
                        + line.get(0).getLineIndex());
                peripheral.add(line);
            }
        }
        if (primary.isEmpty()) return fallback("NO_PRIMARY_ORIENTATION");
        for (List<ScanOcrToken> line : peripheral) {
            for (ScanOcrToken side : line) {
                for (ScanOcrToken main : primary) {
                    if (overlaps(side, main)) return fallback("ORIENTATION_OVERLAP line="
                            + side.getLineIndex());
                }
            }
        }
        for (ScanOcrToken token : primary) {
            angles.add((double) token.getRotation());
            heights.add(token.getHeight());
        }
        Collections.sort(angles);
        Collections.sort(heights);
        double angle = angles.get(angles.size() / 2);
        for (double a : angles) {
            if (Math.abs(a - angle) > MAX_PERSPECTIVE_SPREAD)
                return fallback("INCONSISTENT_ROTATION angle=" + a + " median=" + angle);
        }
        double radians = Math.toRadians(angle);
        double sin = Math.sin(radians), cos = Math.cos(radians);
        List<Point> points = new ArrayList<>();
        for (ScanOcrToken t : primary) {
            points.add(new Point(t, t.getCenterX() * cos + t.getCenterY() * sin,
                    t.getCenterY() * cos - t.getCenterX() * sin));
        }
        points.sort(Comparator.comparingDouble((Point p) -> p.y).thenComparingDouble(p -> p.x));
        List<List<Point>> groups = new ArrayList<>();
        for (Point p : points) {
            List<Point> best = null;
            double nearest = Double.MAX_VALUE;
            for (List<Point> row : groups) {
                Point anchor = row.get(0);
                double tolerance = 0.55 * Math.min(heights.get(heights.size() / 2),
                        Math.min(anchor.token.getHeight(), p.token.getHeight()));
                double distance = Math.abs(p.y - anchor.y);
                if (distance <= tolerance && distance < nearest) {
                    best = row;
                    nearest = distance;
                }
            }
            if (best == null) {
                best = new ArrayList<>();
                groups.add(best);
            }
            best.add(p);
        }
        List<List<ScanOcrToken>> result = new ArrayList<>();
        for (List<Point> group : groups) {
            group.sort(Comparator.comparingDouble(p -> p.x));
            List<ScanOcrToken> row = new ArrayList<>();
            for (Point p : group) row.add(p.token);
            result.add(row);
        }
        // Keep peripheral codes as separate trailing rows, never merged with composition.
        // The caller also retains the untouched fullText and original lines for audit.
        result.addAll(peripheral);
        if (!peripheral.isEmpty()) Log.d("VENUS_BACK_PARSE",
                "BACK_ORIENTATION_GROUPS primaryTokens=" + primary.size()
                        + " peripheralLines=" + peripheral.size());
        return result;
    }

    private static boolean overlaps(ScanOcrToken a, ScanOcrToken b) {
        return a.getBoundingBox().left < b.getBoundingBox().right
                && b.getBoundingBox().left < a.getBoundingBox().right
                && a.getBoundingBox().top < b.getBoundingBox().bottom
                && b.getBoundingBox().top < a.getBoundingBox().bottom;
    }

    private static List<List<ScanOcrToken>> fallback(String reason) {
        Log.d("VENUS_BACK_PARSE", "BACK_LAYOUT_FALLBACK_REASON=" + reason);
        return Collections.emptyList();
    }

    static String join(List<ScanOcrToken> tokens) {
        List<String> text = new ArrayList<>();
        for (ScanOcrToken token : tokens) text.add(token.getText().trim());
        return String.join(" ", text);
    }

    private static String compact(String text) {
        return text.replaceAll("\\s+", "");
    }

    private static final class Point {
        final ScanOcrToken token;
        final double x, y;
        Point(ScanOcrToken token, double x, double y) {
            this.token = token;
            this.x = x;
            this.y = y;
        }
    }
}
