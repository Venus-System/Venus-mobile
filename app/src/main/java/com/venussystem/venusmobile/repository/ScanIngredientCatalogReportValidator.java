package com.venussystem.venusmobile.repository;

import com.venussystem.venusmobile.model.ScanBackIngredientCandidate;
import com.venussystem.venusmobile.domain.scan.ScanIngredientKeyNormalizer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks that catalogue evidence still describes exactly the OCR candidates
 * that will be sent. A malformed or stale report is downgraded to raw review
 * data instead of being allowed to attach an unrelated ingredient ID.
 */
public final class ScanIngredientCatalogReportValidator {
    private ScanIngredientCatalogReportValidator() { }

    public static void validate(
            ScanIngredientCatalog.Report report,
            List<ScanBackIngredientCandidate> candidates
    ) {
        if (report == null || report.matches == null) {
            throw new IllegalArgumentException("CATALOG_REPORT_MISSING");
        }
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("CATALOG_CANDIDATES_MISSING");
        }
        if (report.expectedCount != candidates.size()
                || report.matches.size() != candidates.size()) {
            throw new IllegalArgumentException("CATALOG_REPORT_COUNT_MISMATCH");
        }
        Set<Integer> candidatePositions = new HashSet<>();
        for (ScanBackIngredientCandidate candidate : candidates) {
            if (candidate == null || candidate.getPosition() <= 0
                    || !candidatePositions.add(candidate.getPosition())) {
                throw new IllegalArgumentException("CATALOG_CANDIDATE_POSITION_INVALID");
            }
        }
        Set<Integer> reportPositions = new HashSet<>();
        Map<Integer, ScanBackIngredientCandidate> candidatesByPosition = new HashMap<>();
        for (ScanBackIngredientCandidate candidate : candidates) {
            candidatesByPosition.put(candidate.getPosition(), candidate);
        }
        for (ScanIngredientCatalog.Match match : report.matches) {
            if (match == null || match.position <= 0 || !reportPositions.add(match.position)
                    || !candidatePositions.contains(match.position)) {
                throw new IllegalArgumentException("CATALOG_REPORT_POSITION_INVALID");
            }
            ScanBackIngredientCandidate candidate = candidatesByPosition.get(match.position);
            String reportKey = match == null ? "" : ScanIngredientKeyNormalizer.normalize(match.normalizedName);
            String rawKey = candidate == null ? "" : ScanIngredientKeyNormalizer.normalize(candidate.getRawName());
            String correctedKey = candidate == null ? "" : ScanIngredientKeyNormalizer.normalize(candidate.getNormalizedName());
            if (candidate == null || match.rawName == null || match.rawName.trim().isEmpty()
                    || !match.rawName.equals(candidate.getRawName())
                    || reportKey.isEmpty()
                    || (!reportKey.equals(rawKey) && !reportKey.equals(correctedKey))) {
                throw new IllegalArgumentException("CATALOG_REPORT_EVIDENCE_MISMATCH");
            }
            boolean resolved = ScanIngredientCatalog.RESOLUTION_RESOLVED.equals(match.resolutionStatus);
            if (resolved) {
                if (match.ingredientId == null || match.ingredientId <= 0
                        || match.inciName == null || match.inciName.trim().isEmpty()
                        || match.needsReview) {
                    throw new IllegalArgumentException("CATALOG_RESOLUTION_INVALID");
                }
            } else {
                if (match.ingredientId != null || (match.inciName != null && !match.inciName.trim().isEmpty())
                        || !match.needsReview) {
                    throw new IllegalArgumentException("CATALOG_UNRESOLVED_INVALID");
                }
            }
        }
        if (!candidatePositions.equals(reportPositions)) {
            throw new IllegalArgumentException("CATALOG_REPORT_POSITIONS_MISMATCH");
        }
        int resolved = 0;
        int unresolved = 0;
        for (ScanIngredientCatalog.Match match : report.matches) {
            if (ScanIngredientCatalog.RESOLUTION_RESOLVED.equals(match.resolutionStatus)) resolved++;
            else unresolved++;
        }
        if (resolved != report.resolved || unresolved != report.unresolved
                || resolved + unresolved != report.expectedCount) {
            throw new IllegalArgumentException("CATALOG_REPORT_COUNTS_INVALID");
        }
        String expectedStatus = unresolved == 0
                ? ScanIngredientCatalog.VALIDATION_VALID
                : ScanIngredientCatalog.VALIDATION_NEEDS_REVIEW;
        if (!expectedStatus.equals(report.validationStatus)) {
            throw new IllegalArgumentException("CATALOG_REPORT_STATUS_INVALID");
        }
        if ((unresolved > 0) != report.needsReview) {
            throw new IllegalArgumentException("CATALOG_REPORT_REVIEW_FLAG_INVALID");
        }
        if (report.issues != null && !report.issues.isEmpty()) {
            Set<Integer> issuePositions = new HashSet<>();
            for (ScanIngredientCatalog.Issue issue : report.issues) {
                if (issue == null || issue.position <= 0 || !issuePositions.add(issue.position)) {
                    throw new IllegalArgumentException("CATALOG_ISSUE_POSITION_INVALID");
                }
                ScanIngredientCatalog.Match match = report.matches.stream()
                        .filter(item -> item.position == issue.position)
                        .findFirst().orElse(null);
                if (match == null || !match.needsReview) {
                    throw new IllegalArgumentException("CATALOG_ISSUE_WITHOUT_REVIEW_MATCH");
                }
            }
        }
    }
}
