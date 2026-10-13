package com.venussystem.venusmobile.domain.scan;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Pattern;

/** Conservative local checks. Unknown text is retained and requires review. */
public final class ScanIngredientPolicy {
    private static final Pattern ADMIN = Pattern.compile(
            "\\b(?:UNILEVER|ONILEVER|ONILEVEW|WOFENSNO|LTDA|CNPJ|CEP|"
                    + "WHATSAPP|WWW|HTTPS?|SAC|LOTE|BATCH)\\b");
    private static final Map<String, String> OCR_ALIASES = new HashMap<>();
    static {
        OCR_ALIASES.put("PPG-14 BUTL ETE", "PPG-14 BUTYL ETHER");
        OCR_ALIASES.put("PPG-14 BUTY ETHER", "PPG-14 BUTYL ETHER");
        OCR_ALIASES.put("ROPYLENE CARBONATE", "PROPYLENE CARBONATE");
        OCR_ALIASES.put("EJANTHUS ANNUUS SEED OIL", "HELIANTHUS ANNUUS SEED OIL");
        OCR_ALIASES.put("0CTYLDODECANO", "OCTYLDODECANOL");
        OCR_ALIASES.put("ALPHA-ISOMETHYL ONN INIO", "ALPHA-ISOMETHYL IONONE");
    }
    private ScanIngredientPolicy() { }

    public static String normalize(String raw) {
        return raw == null ? "" : Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toUpperCase(Locale.ROOT).trim()
                .replaceAll("\\s+", " ");
    }

    public static boolean hasAdministrativeText(String raw) {
        String value = normalize(raw);
        if (ADMIN.matcher(value).find()) return true;
        // A standalone OCR line containing the manufacturer name is noise;
        // keep mixed/uncertain candidates for administrative review.
        return value.matches("^UNILEVEV(?:\\s+[0-9]{4,})?$");
    }

    public static boolean isRecognized(String raw) {
        String value = normalize(raw);
        return ScanIngredientVocabulary.NAMES.contains(value) || value.matches("CI ?[0-9]{5}");
    }

    /** A small unambiguous change to ONE complete candidate; no formula completion. */
    public static String correct(String raw) {
        String value = normalize(raw);
        if (isRecognized(value)) return value;
        String alias = OCR_ALIASES.get(value);
        if (alias != null) return alias;
        String letters = value.replace('0', 'O');
        if (value.indexOf('0') >= 0 && !value.matches(".*[1-9].*")
                && ScanIngredientVocabulary.NAMES.contains(letters)) return letters;
        if (value.length() < 5 || !value.matches("[A-Z -]+")) return value;
        String best = null;
        for (String known : ScanIngredientVocabulary.NAMES) {
            if (!known.matches("[A-Z -]+")
                    || known.split(" ").length != value.split(" ").length) continue;
            if (oneEdit(value, known)) {
                if (best != null) return value;
                best = known;
            }
        }
        return best == null ? value : best;
    }

    private static boolean oneEdit(String a, String b) {
        if (Math.abs(a.length() - b.length()) > 1) return false;
        int i = 0, j = 0, edits = 0;
        while (i < a.length() && j < b.length()) {
            if (a.charAt(i) == b.charAt(j)) { i++; j++; continue; }
            if (++edits > 1) return false;
            if (a.length() >= b.length()) i++;
            if (b.length() >= a.length()) j++;
        }
        return edits + (a.length() - i) + (b.length() - j) == 1;
    }

    /** Contextual section-tail hint, never a generic ingredient discard rule. */
    public static boolean isPackagingTailLine(String raw) {
        String value = normalize(raw);
        if (hasAdministrativeText(value)) return true;
        String compact = value.replace(" ", "");
        return !value.matches("^(?:CI|PEG|PPG|C[0-9]).*")
                && compact.matches("[A-Z0-9]{4,14}") && compact.matches(".*[0-9].*")
                && compact.replaceAll("[0-9]", "").length() <= 4;
    }
}
