package com.venussystem.venusmobile.model;

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Rascunho local de uma submissão de produto não cadastrado.
 *
 * Não salva em Mongo e não faz upload.
 * Apenas transporta os dados entre as Activities durante o fluxo atual.
 */
public class ScanSubmissionDraft implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String EXTRA_DRAFT =
            "scan_submission_draft";

    public static final String STATUS_FRONT_CONFIRMED =
            "FRONT_CONFIRMED";

    public static final String STATUS_WAITING_BACK =
            "WAITING_BACK";

    public static final String STATUS_BACK_PROCESSED =
            "BACK_PROCESSED";

    public static final String STATUS_PENDING_ADMIN_REVIEW =
            "PENDING_ADMIN_REVIEW";

    public static final String STATUS_WAITING_UPLOAD = "WAITING_UPLOAD";

    private String status;
    private String scanId = UUID.randomUUID().toString();
    private String startedAt = OffsetDateTime.now().toString();
    private String finishedAt;
    private String ownerUid;
    private ScanPhotoQuality photoQuality;

    public String getScanId() { return scanId; }
    public String getStartedAt() { return startedAt; }
    public String getFinishedAt() { return finishedAt; }
    public String getOwnerUid() { return ownerUid; }
    public ScanPhotoQuality getPhotoQuality() { return photoQuality; }
    public void setPhotoQuality(ScanPhotoQuality quality) { photoQuality = quality; }
    public void bindToUser(String uid) {
        if (uid == null || uid.trim().isEmpty()) throw new IllegalArgumentException("Usuário ausente.");
        if (ownerUid != null && !ownerUid.equals(uid))
            throw new IllegalStateException("O rascunho pertence a outra conta.");
        ownerUid = uid;
    }

    private String frontPhotoPath;
    private String frontOcrText;
    private List<String> frontOcrLines;

    private List<String> brandCandidates;
    private List<String> productCandidates;
    private List<String> presentationCandidates;

    private String capacity;
    private String concentration;

    private ScanCosmeticClassification classification;

    private String backPhotoPath;
    private String backOcrText;
    private List<String> backOcrLines;
    private ScanBackData backData;

    public ScanSubmissionDraft() {
        status = STATUS_FRONT_CONFIRMED;
        frontOcrLines = new ArrayList<>();
        brandCandidates = new ArrayList<>();
        productCandidates = new ArrayList<>();
        presentationCandidates = new ArrayList<>();
        backOcrLines = new ArrayList<>();
    }

    @NonNull
    public static ScanSubmissionDraft fromFront(
            @NonNull String photoPath,
            @NonNull ScanOcrResult ocr,
            @NonNull ScanFrontData frontData,
            @NonNull ScanCosmeticClassification classification
    ) {
        ScanSubmissionDraft draft =
                new ScanSubmissionDraft();

        draft.frontPhotoPath = photoPath;
        draft.frontOcrText = ocr.getFullText();
        draft.frontOcrLines =
                new ArrayList<>(ocr.getLines());

        draft.brandCandidates =
                new ArrayList<>(
                        frontData.getBrandCandidates()
                );

        draft.productCandidates =
                new ArrayList<>(
                        frontData.getProductCandidates()
                );

        draft.presentationCandidates =
                new ArrayList<>(
                        frontData.getPresentationCandidates()
                );

        draft.capacity =
                frontData.getCapacity();

        draft.concentration =
                frontData.getConcentration();

        draft.classification =
                classification;

        draft.status =
                STATUS_WAITING_BACK;

        return draft;
    }

    @Nullable
    public static ScanSubmissionDraft fromIntent(
            @NonNull Intent intent
    ) {
        return intent.getSerializableExtra(
                EXTRA_DRAFT,
                ScanSubmissionDraft.class
        );
    }

    public void putInto(
            @NonNull Intent intent
    ) {
        intent.putExtra(
                EXTRA_DRAFT,
                this
        );
    }

    @NonNull
    public String getStatus() {
        return status == null ? "" : status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @NonNull
    public String getFrontPhotoPath() {
        return frontPhotoPath == null ? "" : frontPhotoPath;
    }

    public void setFrontPhotoPath(String frontPhotoPath) {
        this.frontPhotoPath = frontPhotoPath;
    }

    @NonNull
    public String getFrontOcrText() {
        return frontOcrText == null ? "" : frontOcrText;
    }

    @NonNull
    public List<String> getFrontOcrLines() {
        return Collections.unmodifiableList(frontOcrLines);
    }

    @NonNull
    public List<String> getBrandCandidates() {
        return Collections.unmodifiableList(brandCandidates);
    }

    @NonNull
    public List<String> getProductCandidates() {
        return Collections.unmodifiableList(productCandidates);
    }

    @NonNull
    public List<String> getPresentationCandidates() {
        return Collections.unmodifiableList(presentationCandidates);
    }

    @NonNull
    public String getCapacity() {
        return capacity == null ? "" : capacity;
    }

    @NonNull
    public String getConcentration() {
        return concentration == null ? "" : concentration;
    }

    @Nullable
    public ScanCosmeticClassification getClassification() {
        return classification;
    }

    @Nullable
    public String getBackPhotoPath() {
        return backPhotoPath;
    }

    public void setBackPhotoPath(String backPhotoPath) {
        this.backPhotoPath = backPhotoPath;
    }

    @NonNull
    public String getBackOcrText() {
        return backOcrText == null ? "" : backOcrText;
    }

    public void setBackOcrText(String backOcrText) {
        this.backOcrText = backOcrText;
    }

    @NonNull
    public List<String> getBackOcrLines() {
        return Collections.unmodifiableList(backOcrLines);
    }

    public void setBackOcrLines(
            @NonNull List<String> lines
    ) {
        this.backOcrLines =
                new ArrayList<>(lines);
    }

    @Nullable
    public ScanBackData getBackData() {
        return backData;
    }

    public void setBackData(
            @Nullable ScanBackData backData
    ) {
        this.backData = backData;
        if (backData != null) {
            this.status = STATUS_BACK_PROCESSED;
            if (finishedAt == null) finishedAt = OffsetDateTime.now().toString();
        }
    }
}
