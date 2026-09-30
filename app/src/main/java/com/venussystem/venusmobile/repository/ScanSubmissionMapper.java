package com.venussystem.venusmobile.repository;

import com.venussystem.venusmobile.model.ScanBackData;
import com.venussystem.venusmobile.model.ScanSubmissionDraft;
import com.venussystem.venusmobile.model.ScanPhotoQuality;
import com.venussystem.venusmobile.repository.api.dto.ScanSessionRequest;
import java.util.ArrayList;
import java.util.List;

/** Preserves raw OCR. Ambiguous front candidates are not promoted to confirmed fields. */
public final class ScanSubmissionMapper {
    private ScanSubmissionMapper() { }

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
        front.brand = unambiguous(draft.getBrandCandidates());
        front.productName = unambiguous(draft.getProductCandidates());
        front.presentation = unambiguous(draft.getPresentationCandidates());
        front.capacity = draft.getCapacity();
        // Product type is not a backend category, nor are back claims front claims.
        request.ocr.front.extracted = front;
        request.ocr.back = new ScanSessionRequest.Back();
        request.ocr.back.fullText = draft.getBackOcrText();
        request.ocr.back.lines = new ArrayList<>(draft.getBackOcrLines());
        ScanBackData data = draft.getBackData();
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
        data.getIngredients().forEach(i -> request.ingredients.add(
                new ScanSessionRequest.Ingredient(i.getPosition(), i.getRawName())));
        return request;
    }

    private static String unambiguous(List<String> values) {
        return values.size() == 1 ? values.get(0) : null;
    }
}
