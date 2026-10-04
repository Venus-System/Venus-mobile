package com.venussystem.venusmobile.repository;

import com.venussystem.venusmobile.model.ScanBackData;
import com.venussystem.venusmobile.domain.scan.ScanIngredientPolicy;
import com.venussystem.venusmobile.model.ScanBackIngredientCandidate;
import com.venussystem.venusmobile.model.ScanSubmissionDraft;
import com.venussystem.venusmobile.model.ScanPhotoQuality;
import com.venussystem.venusmobile.repository.api.dto.ScanSessionRequest;
import android.util.Log;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Preserves raw OCR and selects the strongest front candidate without trusting OCR blindly. */
public final class ScanSubmissionMapper {
    private ScanSubmissionMapper() { }
    private static final Set<String> GENERIC_FRONT_WORDS = new HashSet<>(Arrays.asList(
            "CREME", "LOCAO", "GEL", "SPRAY", "AEROSOL", "FRESH", "ORIGINAL",
            "PROTECAO", "ANTITRANSPIRANTE", "ANTIPERSPIRANTE", "PERFUME", "MASCULINO",
            "FEMININO", "INFANTIL", "DESODORANTE", "HIDRATANTE", "NOVO", "NOVA",
            "SHAMPOO", "CONDICIONADOR", "SABONETE", "LOÇÃO", "CREME", "BODY",
            "LOTION", "DEODORANT", "PERFUME", "KIT", "PRODUTO"
    ));

    public static ScanSessionRequest prepare(ScanSubmissionDraft draft, String uid,
            ScanSessionRequest.Device device, ScanSessionRequest.QualityCheck quality) {
        if (draft == null || draft.getBackData() == null)
            throw new IllegalArgumentException("A leitura da frente e do verso precisa ser concluída.");
        if (uid == null || !uid.equals(draft.getOwnerUid()))
            throw new IllegalArgumentException("O rascunho pertence a outra conta.");
        ScanSessionRequest request = new ScanSessionRequest();
        request.scanId = draft.getScanId();
        request.firebaseUid = uid;
        request.startedAt = draft.getStartedAt();
        request.finishedAt = draft.getFinishedAt();
        request.device = device;
        request.qualityCheck = quality;
        if (request.qualityCheck == null && draft.getPhotoQuality() != null) {
            ScanPhotoQuality q = draft.getPhotoQuality();
            request.qualityCheck = new ScanSessionRequest.QualityCheck(q.getBlurScore(),
                    q.getBrightness(), q.isBackgroundOk(), q.getStatus());
        }
        request.images = new ScanSessionRequest.Images();
        request.ocr = new ScanSessionRequest.Ocr();
        request.ocr.front = new ScanSessionRequest.Front();
        request.ocr.front.fullText = draft.getFrontOcrText();
        request.ocr.front.lines = new ArrayList<>(draft.getFrontOcrLines());
        ScanSessionRequest.FrontExtracted front = new ScanSessionRequest.FrontExtracted();
        front.brand = bestFrontCandidate(draft.getBrandCandidates());
        front.productName = bestFrontCandidate(draft.getProductCandidates());
        front.presentation = bestFrontCandidate(draft.getPresentationCandidates());
        front.capacity = draft.getCapacity();
        // Product type is not a backend category, nor are back claims front claims.
        request.ocr.front.extracted = front;
        request.ocr.back = new ScanSessionRequest.Back();
        request.ocr.back.fullText = draft.getBackOcrText();
        request.ocr.back.lines = new ArrayList<>(draft.getBackOcrLines());
        ScanBackData data = draft.getBackData();
        if (!data.isIngredientSectionFound())
            throw new IllegalArgumentException("A composição precisa de uma nova leitura.");
        ScanSessionRequest.BackExtracted back = new ScanSessionRequest.BackExtracted();
        back.ingredientsText = data.getIngredientsRawText();
        back.manufacturer = data.getManufacturer();
        back.manufacturerAddress = data.getManufacturerAddress();
        back.contact = data.getContact();
        back.country = data.getCountry();
        back.batch = data.getBatch();
        back.registrationNumber = data.getRegistrationNumber();
        back.netContent = data.getNetContent();
        back.usage = data.getUsage();
        back.precautions = data.getPrecautions();
        back.warnings = data.getWarnings();
        back.barcode = data.getBarcode();
        back.otherText = data.getOtherText();
        back.claims = new ArrayList<>(data.getClaims());
        request.ocr.back.extracted = back;
        request.ingredients = new ArrayList<>();
        int dropped = 0;
        for (ScanBackIngredientCandidate i : data.getIngredients()) {
            String wireName = ingredientNameForWire(i);
            if (ScanIngredientPolicy.hasAdministrativeText(wireName)) {
                dropped++;
                continue;
            }
            request.ingredients.add(new ScanSessionRequest.Ingredient(i.getPosition(), wireName));
        }
        if (dropped > 0) {
            Log.d("VENUS_SCAN_SUBMISSION", "INGREDIENTS_FILTERED count=" + dropped);
        }
        ScanSubmissionValidator.validateIngredientQuality(request);
        return request;
    }

    /**
     * The existing API accepts only rawName and computes normalizedName server-side.
     * For high-confidence OCR corrections, send the canonical value so Mongo matching
     * can resolve it; the original OCR remains in fullText/ingredientsText for audit.
     */
    private static String ingredientNameForWire(ScanBackIngredientCandidate candidate) {
        // Re-evaluate saved candidates too; do not trust old aggressive corrections.
        String raw = candidate.getRawName();
        String corrected = ScanIngredientPolicy.correct(raw);
        return corrected.equals(ScanIngredientPolicy.normalize(raw)) ? raw : corrected;
    }

    private static String bestFrontCandidate(List<String> values) {
        if (values == null || values.isEmpty()) return null;
        boolean hasInformativeCandidate = false;
        for (String candidate : values) {
            if (isInformativeFrontCandidate(candidate)) {
                hasInformativeCandidate = true;
                break;
            }
        }
        String best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int index = 0; index < values.size(); index++) {
            String candidate = values.get(index);
            if (candidate == null) continue;
            String value = candidate.trim().replaceAll("\\s+", " ");
            if (value.length() < 2 || value.length() > 80 || !value.matches(".*[A-Za-zÀ-ÿ].*")) continue;
            String normalized = value.toUpperCase(Locale.ROOT);
            if (hasInformativeCandidate && !isInformativeFrontCandidate(value)) {
                continue;
            }
            int score = 100 - index * 3;
            int words = normalized.split("\\s+").length;
            score += Math.min(words, 3) * 4;
            if (value.length() <= 40) score += 5;
            if (normalized.matches(".*\\d.*")) score -= 12;
            for (String token : normalized.split("\\s+")) {
                if (GENERIC_FRONT_WORDS.contains(token)) score -= 18;
            }
            if (score > bestScore) {
                best = value;
                bestScore = score;
            }
        }
        return best;
    }

    private static boolean isInformativeFrontCandidate(String candidate) {
        if (candidate == null) return false;
        String normalized = candidate.trim()
                .replaceAll("\\s+", " ")
                .toUpperCase(Locale.ROOT);
        if (normalized.length() < 2) return false;
        for (String token : normalized.split("\\s+")) {
            String clean = token.replaceAll("[^A-ZÀ-ÿ]", "");
            if (clean.isEmpty()) continue;
            if (!GENERIC_FRONT_WORDS.contains(clean)) return true;
        }
        return false;
    }
}
