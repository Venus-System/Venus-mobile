package com.venussystem.venusmobile.repository;

import com.venussystem.venusmobile.repository.api.dto.ScanSessionRequest;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Rejects invalid data rather than silently truncating OCR to fit the server. */
public final class ScanSubmissionValidator {
    private ScanSubmissionValidator() { }

    public static void validate(ScanSessionRequest r, boolean requireUploadedImages) {
        require(r != null, "Submissão ausente.");
        try { require(UUID.fromString(r.scanId).toString().equals(r.scanId), "scanId inválido."); }
        catch (RuntimeException e) { throw new IllegalArgumentException("scanId inválido."); }
        required(r.firebaseUid, 128, "Usuário Firebase");
        require(r.device != null, "Dados do aparelho ausentes.");
        required(r.device.deviceId, Integer.MAX_VALUE, "Identificador da instalação");
        required(r.device.platform, Integer.MAX_VALUE, "Plataforma");
        required(r.device.appVersion, Integer.MAX_VALUE, "Versão do app");
        required(r.device.model, Integer.MAX_VALUE, "Modelo do aparelho");
        try {
            require(!OffsetDateTime.parse(r.finishedAt).isBefore(OffsetDateTime.parse(r.startedAt)),
                    "Data final anterior à data inicial.");
        } catch (RuntimeException e) { throw new IllegalArgumentException("Datas do scan inválidas."); }
        require(r.qualityCheck != null, "Avaliação de qualidade ainda não informada.");
        require(r.qualityCheck.blurScore != null && Double.isFinite(r.qualityCheck.blurScore)
                && r.qualityCheck.brightness != null && Double.isFinite(r.qualityCheck.brightness)
                && r.qualityCheck.backgroundOk != null,
                "A API exige medições reais de qualidade; não é possível inventar esses valores.");
        require(Arrays.asList("PROCESSING", "COMPLETED", "FAILED", "PENDING_REVIEW")
                .contains(r.qualityCheck.status), "Status de qualidade inválido.");
        require(r.ocr != null && r.ocr.front != null && r.ocr.back != null,
                "Este fluxo exige o OCR da frente e do verso.");
        text(r.ocr.front.fullText, 20000, "OCR da frente");
        text(r.ocr.back.fullText, 20000, "OCR do verso");
        strings(r.ocr.front.lines, 500, 1000, "Linhas da frente");
        strings(r.ocr.back.lines, 500, 1000, "Linhas do verso");
        ScanSessionRequest.FrontExtracted f = r.ocr.front.extracted;
        if (f != null) {
            for (String s : Arrays.asList(f.brand, f.productName, f.presentation, f.capacity, f.category))
                text(s, 2000, "Campo extraído da frente");
            strings(f.claims, 50, 200, "Alegações da frente");
        }
        ScanSessionRequest.BackExtracted b = r.ocr.back.extracted;
        if (b != null) {
            text(b.ingredientsText, 20000, "Texto da composição");
            for (String s : Arrays.asList(b.manufacturer, b.manufacturerAddress, b.contact, b.country,
                    b.batch, b.registrationNumber, b.netContent, b.usage, b.precautions,
                    b.warnings, b.barcode, b.otherText)) text(s, 2000, "Campo extraído do verso");
            strings(b.claims, 50, 200, "Alegações do verso");
        }
        require(r.ingredients != null && r.ingredients.size() <= 200, "Limite de 200 ingredientes excedido.");
        Set<Integer> positions = new HashSet<>();
        for (ScanSessionRequest.Ingredient i : r.ingredients) {
            require(i != null && i.position != null && i.position > 0 && positions.add(i.position),
                    "Posição de ingrediente inválida ou repetida.");
            required(i.rawName, 300, "Nome bruto do ingrediente");
        }
        if (requireUploadedImages) {
            require(r.images != null && r.images.front != null && r.images.back != null,
                    "As duas fotos precisam ser enviadas antes do cadastro.");
            image(r.images.front, r.scanId, "front");
            image(r.images.back, r.scanId, "back");
        }
    }

    public static void image(ScanSessionRequest.Image image, String scanId, String side) {
        require(image != null, "Referência de imagem ausente.");
        required(image.publicId, 255, "Identificador da imagem");
        String expected = "scans/" + scanId + "/" + side;
        require(image.publicId.equals(expected) || image.publicId.endsWith("/" + expected),
                "A imagem não pertence ao scan/lado esperado.");
        require(image.width != null && image.width > 0 && image.height != null && image.height > 0
                && image.bytes != null && image.bytes > 0, "Metadados da imagem inválidos.");
        required(image.format, 10, "Formato da imagem");
    }
    private static void strings(List<String> values, int count, int length, String name) {
        if (values == null) return;
        require(values.size() <= count, name + ": limite excedido.");
        for (String value : values) text(value, length, name);
    }
    private static void required(String value, int max, String name) {
        require(value != null && !value.trim().isEmpty(), name + " obrigatório.");
        text(value, max, name);
    }
    private static void text(String value, int max, String name) {
        require(value == null || value.length() <= max, name + ": limite de " + max + " caracteres excedido.");
    }
    private static void require(boolean valid, String message) {
        if (!valid) throw new IllegalArgumentException(message);
    }
}
