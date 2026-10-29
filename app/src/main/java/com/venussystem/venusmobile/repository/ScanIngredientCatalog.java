package com.venussystem.venusmobile.repository;

import com.venussystem.venusmobile.domain.scan.ScanIngredientKeyNormalizer;
import com.venussystem.venusmobile.domain.scan.ScanIngredientPolicy;
import com.venussystem.venusmobile.repository.api.ScanApiException;
import com.venussystem.venusmobile.repository.api.ScanIngredientApi;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import retrofit2.Response;

public final class ScanIngredientCatalog {
    public static final String STATUS_KNOWN = "KNOWN";
    public static final String STATUS_ALIAS = "ALIAS";
    public static final String STATUS_COMMON_NAME = "COMMON_NAME";
    public static final String STATUS_AMBIGUOUS = "AMBIGUOUS";
    public static final String STATUS_NOT_FOUND = "NOT_FOUND";
    public static final String STATUS_ADMINISTRATIVE_NOISE = "ADMINISTRATIVE_NOISE";
    public static final String STATUS_LOOKUP_ERROR = "LOOKUP_ERROR";
    public static final String RESOLUTION_RESOLVED = "RESOLVED";
    public static final String RESOLUTION_AMBIGUOUS = "AMBIGUOUS";
    public static final String RESOLUTION_UNRESOLVED = "UNRESOLVED";
    public static final String VALIDATION_VALID = "VALID";
    public static final String VALIDATION_NEEDS_REVIEW = "NEEDS_REVIEW";
    public static final String ISSUE_OPEN = "OPEN";

    public interface AccountCheck { void run() throws IOException; }
    private static final long CACHE_TTL_MS = 24L * 60L * 60L * 1000L;
    private static final double FUZZY_THRESHOLD_LONG = .84d;
    private static final double FUZZY_THRESHOLD_SHORT = .92d;
    private static final double AMBIGUITY_MARGIN = .05d;
    private static final Map<String, CachedMatch> CACHE = new LinkedHashMap<String, CachedMatch>(32, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, CachedMatch> eldest) { return size() > 256; }
    };

    private final ScanIngredientApi api;
    private final AccountCheck checkAccount;
    private final ScanIngredientCatalogCache snapshotCache;

    private static final class CachedMatch {
        final Match match; final long storedAt;
        CachedMatch(Match match) { this.match = match; storedAt = System.currentTimeMillis(); }
    }

    public ScanIngredientCatalog(ScanIngredientApi api, AccountCheck checkAccount) {
        this(api, checkAccount, null);
    }

    public ScanIngredientCatalog(ScanIngredientApi api, AccountCheck checkAccount,
                                 ScanIngredientCatalogCache snapshotCache) {
        if (api == null || checkAccount == null) throw new IllegalArgumentException("CATALOG_DEPENDENCY_MISSING");
        this.api = api; this.checkAccount = checkAccount; this.snapshotCache = snapshotCache;
    }

    public static final class Report {
        public String checkedAt;
        public long catalogUpdatedAtEpochMs;
        public boolean cacheFresh;
        public int catalogSize;
        public int expectedCount;
        public String validationStatus;
        public List<Match> matches = new ArrayList<>();
        public List<Issue> issues = new ArrayList<>();
        public int known, alias, commonName, fuzzy, ambiguous, notFound, errors, resolved, unresolved;
        public boolean needsReview;
    }

    public static final class Issue {
        public int position;
        public String type;
        public String rawName;
        public String normalizedName;
        public String reason;
        public String status = ISSUE_OPEN;
    }

    public static final class Match {
        public int position;
        public String rawName, normalizedName, status, resolutionStatus, resolutionMethod, inciName;
        public double confidence;
        public boolean needsReview;
        public Long ingredientId;
        public Integer errorCode;
        public List<Long> candidateIds = new ArrayList<>();
    }

    public Report lookup(List<String> rawNames) throws IOException {
        validateNames(rawNames);
        return snapshotCache == null ? lookupExactCompatibility(rawNames) : lookupFromSnapshot(rawNames);
    }

    private Report lookupFromSnapshot(List<String> rawNames) throws IOException {
        ScanIngredientCatalogCache.Snapshot snapshot = null;
        boolean fresh = false;
        try { snapshot = snapshotCache.readFresh(CACHE_TTL_MS); }
        catch (IOException ignored) { }
        if (snapshot != null) {
            fresh = true;
        } else {
            try {
                snapshot = refreshSnapshot();
                fresh = true;
            } catch (IOException refreshError) {
                try { snapshot = snapshotCache.read(); }
                catch (IOException ignored) { snapshot = null; }
                if (snapshot == null) throw refreshError;
            }
        }
        checkAccount.run();
        Report result = lookupLocal(rawNames, snapshot, fresh);
        checkAccount.run();
        return result;
    }

    private ScanIngredientCatalogCache.Snapshot refreshSnapshot() throws IOException {
        checkAccount.run();
        Response<List<ScanIngredientApi.Ingredient>> ingredients = api.allIngredients().execute();
        checkResponse(ingredients, "CATALOG_INGREDIENTS");
        checkAccount.run();
        Response<List<ScanIngredientApi.Alias>> aliases = api.allAliases().execute();
        checkResponse(aliases, "CATALOG_ALIASES");
        List<ScanIngredientCatalogCache.IngredientEntry> ingredientEntries = new ArrayList<>();
        Set<Long> ids = new HashSet<>();
        for (ScanIngredientApi.Ingredient item : ingredients.body()) {
            if (item == null || item.id == null || item.id <= 0 || key(item.inciName).isEmpty()) continue;
            if (ids.add(item.id)) ingredientEntries.add(new ScanIngredientCatalogCache.IngredientEntry(
                    item.id, item.inciName.trim(), emptyToNull(item.commonName)));
        }
        List<ScanIngredientCatalogCache.AliasEntry> aliasEntries = new ArrayList<>();
        for (ScanIngredientApi.Alias item : aliases.body()) {
            if (item == null || item.id == null || item.id <= 0 || item.ingredientId == null
                    || item.ingredientId <= 0 || key(item.aliasName).isEmpty()) continue;
            aliasEntries.add(new ScanIngredientCatalogCache.AliasEntry(item.id, item.ingredientId, item.aliasName.trim()));
        }
        if (ingredientEntries.isEmpty()) throw new IOException("CATALOG_EMPTY");
        ScanIngredientCatalogCache.Snapshot snapshot = ScanIngredientCatalogCache.snapshot(ingredientEntries, aliasEntries);
        snapshotCache.write(snapshot);
        return snapshot;
    }

    private Report lookupLocal(List<String> rawNames, ScanIngredientCatalogCache.Snapshot snapshot, boolean fresh) {
        Map<String, List<LocalCandidate>> index = new HashMap<>();
        Map<Long, String> canonicalById = new HashMap<>();
        Set<Long> validIds = new HashSet<>();
        for (ScanIngredientCatalogCache.IngredientEntry item : snapshot.ingredients) {
            if (item == null || item.id == null || item.id <= 0 || key(item.inciName).isEmpty()) continue;
            validIds.add(item.id); canonicalById.put(item.id, item.inciName.trim());
            add(index, new LocalCandidate(item.id, item.inciName, "INCI", key(item.inciName)));
            if (!key(item.commonName).isEmpty()) add(index,
                    new LocalCandidate(item.id, item.inciName, "COMMON_NAME", key(item.commonName)));
        }
        for (ScanIngredientCatalogCache.AliasEntry item : snapshot.aliases) {
            if (item == null || item.ingredientId == null || !validIds.contains(item.ingredientId)
                    || key(item.aliasName).isEmpty()) continue;
            add(index, new LocalCandidate(item.ingredientId, canonicalById.get(item.ingredientId),
                    "ALIAS", key(item.aliasName)));
        }
        Report report = new Report(); report.checkedAt = OffsetDateTime.now().toString();
        report.catalogUpdatedAtEpochMs = snapshot.updatedAtEpochMs; report.cacheFresh = fresh; report.catalogSize = validIds.size();
        for (int i = 0; i < rawNames.size(); i++) {
            Match match = resolveLocal(rawNames.get(i), i + 1, index);
            report.matches.add(match); count(report, match);
        }
        complete(report);
        return report;
    }

    private Match resolveLocal(String raw, int position, Map<String, List<LocalCandidate>> index) {
        String normalized = key(raw); Match match = new Match();
        match.position = position; match.rawName = raw; match.normalizedName = normalized;
        if (ScanIngredientPolicy.hasAdministrativeText(raw)) {
            Match administrative = unresolved(match, STATUS_ADMINISTRATIVE_NOISE);
            administrative.status = STATUS_ADMINISTRATIVE_NOISE;
            return administrative;
        }
        List<LocalCandidate> exact = index.get(normalized);
        if (exact == null || exact.isEmpty()) {
            String corrected = key(ScanIngredientPolicy.correct(raw));
            if (!corrected.isEmpty() && !corrected.equals(normalized)) exact = index.get(corrected);
            if (exact != null && !exact.isEmpty()) return resolved(match, uniqueById(exact), "OCR_VARIANT", .96d);
        }
        if (exact != null && !exact.isEmpty()) {
            String method = preferredMethod(exact);
            return resolved(match, uniqueById(exact), method,
                    "EXACT_INCI".equals(method) ? 1d : "EXACT_ALIAS".equals(method) ? .99d : .98d);
        }
        if (normalized.length() < 5) return unresolved(match, "SHORT_TOKEN");
        Map<Long, LocalCandidate> bestById = new LinkedHashMap<>();
        for (List<LocalCandidate> candidates : index.values()) for (LocalCandidate candidate : candidates) {
            double score = similarity(normalized, candidate.key);
            LocalCandidate current = bestById.get(candidate.ingredientId);
            if (current == null || score > current.score
                    || (score == current.score && sourceRank(candidate.source) < sourceRank(current.source))) {
                bestById.put(candidate.ingredientId, candidate.withScore(score));
            }
        }
        List<LocalCandidate> scored = new ArrayList<>(bestById.values());
        scored.sort(Comparator.comparingDouble((LocalCandidate value) -> value.score).reversed()
                .thenComparingLong(value -> value.ingredientId));
        if (scored.isEmpty()) return unresolved(match, "NO_CANDIDATE");
        LocalCandidate best = scored.get(0);
        double minimum = normalized.length() <= 7 ? FUZZY_THRESHOLD_SHORT : FUZZY_THRESHOLD_LONG;
        if (best.score < minimum) return unresolved(match, "NO_CANDIDATE");
        if (scored.size() > 1 && best.score - scored.get(1).score < AMBIGUITY_MARGIN) {
            Match ambiguous = unresolved(match, "FUZZY_AMBIGUOUS"); ambiguous.status = STATUS_AMBIGUOUS;
            ambiguous.resolutionStatus = RESOLUTION_AMBIGUOUS; ambiguous.confidence = best.score;
            for (int i = 0; i < Math.min(3, scored.size()); i++) ambiguous.candidateIds.add(scored.get(i).ingredientId);
            return ambiguous;
        }
        String method = "ALIAS".equals(best.source) ? "FUZZY_ALIAS"
                : "COMMON_NAME".equals(best.source) ? "FUZZY_COMMON_NAME" : "FUZZY_INCI";
        return resolved(match, Collections.singletonList(best), method, best.score);
    }

    private Report lookupExactCompatibility(List<String> rawNames) throws IOException {
        Report report = new Report(); report.checkedAt = OffsetDateTime.now().toString();
        for (int i = 0; i < rawNames.size(); i++) {
            String raw = rawNames.get(i); String cacheKey = System.identityHashCode(api) + ":" + key(raw);
            checkAccount.run(); Match cached = cached(cacheKey);
            if (cached != null) { cached.rawName = raw; cached.position = i + 1; report.matches.add(cached); count(report, cached); continue; }
            Match match = new Match(); match.position = i + 1; match.rawName = raw; match.normalizedName = key(raw);
            if (ScanIngredientPolicy.hasAdministrativeText(raw)) {
                unresolved(match, STATUS_ADMINISTRATIVE_NOISE);
                match.status = STATUS_ADMINISTRATIVE_NOISE;
                report.matches.add(match); count(report, match); continue;
            }
            Response<ScanIngredientApi.Ingredient> inci = api.ingredientByInci(raw.trim()).execute(); checkAccount.run();
            if (inci.isSuccessful()) {
                ScanIngredientApi.Ingredient body = validIngredient(inci.body()); match.status = STATUS_KNOWN;
                match.resolutionStatus = RESOLUTION_RESOLVED; match.resolutionMethod = "EXACT_INCI"; match.confidence = 1d;
                match.ingredientId = body.id; match.inciName = body.inciName; cache(cacheKey, match);
                report.matches.add(match); count(report, match); continue;
            }
            if (inci.code() != 404 && inci.code() != 400) {
                if (inci.code() == 401 || inci.code() == 403) throw http(inci.code());
                match.status = STATUS_LOOKUP_ERROR; match.resolutionStatus = RESOLUTION_UNRESOLVED; match.errorCode = inci.code();
                match.needsReview = true;
                report.matches.add(match); count(report, match); continue;
            }
            Response<ScanIngredientApi.Alias> alias = api.aliasByName(raw.trim()).execute(); checkAccount.run();
            if (alias.isSuccessful()) {
                ScanIngredientApi.Alias aliasBody = validAlias(alias.body());
                Response<ScanIngredientApi.Ingredient> canonical = api.ingredientById(aliasBody.ingredientId).execute(); checkAccount.run();
                if (!canonical.isSuccessful()) {
                    if (canonical.code() == 401 || canonical.code() == 403) throw http(canonical.code());
                    match.status = STATUS_LOOKUP_ERROR; match.resolutionStatus = RESOLUTION_UNRESOLVED; match.errorCode = canonical.code();
                    match.needsReview = true;
                } else {
                    ScanIngredientApi.Ingredient body = validIngredient(canonical.body()); match.status = STATUS_ALIAS;
                    match.resolutionStatus = RESOLUTION_RESOLVED; match.resolutionMethod = "EXACT_ALIAS"; match.confidence = .99d;
                    match.ingredientId = body.id; match.inciName = body.inciName; cache(cacheKey, match);
                }
            } else if (alias.code() == 404 || alias.code() == 400) {
                match.status = STATUS_NOT_FOUND; match.resolutionStatus = RESOLUTION_UNRESOLVED; match.needsReview = true; cache(cacheKey, match);
            } else {
                if (alias.code() == 401 || alias.code() == 403) throw http(alias.code());
                match.status = STATUS_LOOKUP_ERROR; match.resolutionStatus = RESOLUTION_UNRESOLVED; match.errorCode = alias.code();
                match.needsReview = true;
            }
            report.matches.add(match); count(report, match);
        }
        checkAccount.run();
        complete(report);
        return report;
    }

    private static final class LocalCandidate {
        final Long ingredientId; final String inciName; final String source; final String key; final double score;
        LocalCandidate(Long id, String inci, String source, String key) { this(id, inci, source, key, 0d); }
        LocalCandidate(Long id, String inci, String source, String key, double score) {
            ingredientId = id; inciName = inci; this.source = source; this.key = key; this.score = score;
        }
        LocalCandidate withScore(double value) { return new LocalCandidate(ingredientId, inciName, source, key, value); }
    }
    private static void add(Map<String, List<LocalCandidate>> index, LocalCandidate value) {
        index.computeIfAbsent(value.key, ignored -> new ArrayList<>()).add(value);
    }
    private static List<LocalCandidate> uniqueById(List<LocalCandidate> values) {
        Map<Long, LocalCandidate> unique = new LinkedHashMap<>();
        for (LocalCandidate value : values) unique.putIfAbsent(value.ingredientId, value);
        return new ArrayList<>(unique.values());
    }
    private static Match resolved(Match match, List<LocalCandidate> candidates, String method, double confidence) {
        match.resolutionMethod = method; match.confidence = confidence;
        if (candidates.size() != 1) {
            match.status = STATUS_AMBIGUOUS; match.resolutionStatus = RESOLUTION_AMBIGUOUS;
            match.needsReview = true;
            for (LocalCandidate candidate : candidates) match.candidateIds.add(candidate.ingredientId);
            return match;
        }
        LocalCandidate candidate = candidates.get(0);
        match.status = "INCI".equals(candidate.source) ? STATUS_KNOWN
                : "ALIAS".equals(candidate.source) ? STATUS_ALIAS : STATUS_COMMON_NAME;
        match.resolutionStatus = RESOLUTION_RESOLVED; match.needsReview = false;
        match.ingredientId = candidate.ingredientId; match.inciName = candidate.inciName;
        match.candidateIds.add(candidate.ingredientId); return match;
    }
    private static Match unresolved(Match match, String reason) {
        match.status = STATUS_NOT_FOUND; match.resolutionStatus = RESOLUTION_UNRESOLVED; match.needsReview = true;
        match.resolutionMethod = reason; match.confidence = 0d;
        return match;
    }
    private static String preferredMethod(List<LocalCandidate> candidates) {
        for (LocalCandidate value : candidates) if ("INCI".equals(value.source)) return "EXACT_INCI";
        for (LocalCandidate value : candidates) if ("ALIAS".equals(value.source)) return "EXACT_ALIAS";
        return "EXACT_COMMON_NAME";
    }
    private static int sourceRank(String source) {
        return "INCI".equals(source) ? 0 : "ALIAS".equals(source) ? 1 : 2;
    }
    private static void count(Report report, Match match) {
        if (match.needsReview) {
            report.needsReview = true;
            Issue issue = new Issue();
            issue.position = match.position;
            issue.rawName = match.rawName;
            issue.normalizedName = match.normalizedName;
            issue.reason = match.resolutionMethod;
            issue.type = issueType(match);
            report.issues.add(issue);
        }
        if (STATUS_AMBIGUOUS.equals(match.status)) report.ambiguous++;
        else if (match.resolutionMethod != null && match.resolutionMethod.startsWith("FUZZY")) report.fuzzy++;
        else if (STATUS_KNOWN.equals(match.status)) report.known++;
        else if (STATUS_ALIAS.equals(match.status)) report.alias++;
        else if (STATUS_COMMON_NAME.equals(match.status)) report.commonName++;
        else if (STATUS_LOOKUP_ERROR.equals(match.status)) report.errors++;
        else if (STATUS_NOT_FOUND.equals(match.status) || STATUS_ADMINISTRATIVE_NOISE.equals(match.status)) report.notFound++;
        if (RESOLUTION_RESOLVED.equals(match.resolutionStatus)) report.resolved++; else report.unresolved++;
    }
    private static String issueType(Match match) {
        if (match == null) return "PARSER_ERROR";
        if (STATUS_AMBIGUOUS.equals(match.status)) return STATUS_AMBIGUOUS;
        if ("TRUNCATED_TOKEN".equals(match.resolutionMethod)
                || "SHORT_TOKEN".equals(match.resolutionMethod)) return "TRUNCATED_TOKEN";
        if ("CATALOG_UNAVAILABLE".equals(match.resolutionMethod)) return "SOURCE_CONFLICT";
        if (STATUS_ADMINISTRATIVE_NOISE.equals(match.status)) return STATUS_NOT_FOUND;
        if (STATUS_LOOKUP_ERROR.equals(match.status)) return "SOURCE_CONFLICT";
        return STATUS_NOT_FOUND;
    }
    private static void complete(Report report) {
        report.expectedCount = report.matches == null ? 0 : report.matches.size();
        report.validationStatus = report.resolved + report.unresolved == report.expectedCount
                ? report.unresolved == 0 ? VALIDATION_VALID : VALIDATION_NEEDS_REVIEW
                : "INVALID";
    }
    private static void validateNames(List<String> rawNames) throws IOException {
        if (rawNames == null || rawNames.isEmpty() || rawNames.size() > 200) throw new IOException("Lista de ingredientes ausente ou fora do limite.");
        for (String raw : rawNames) if (raw == null || key(raw).isEmpty() || raw.length() > 300) throw invalid();
    }
    private static void checkResponse(Response<?> response, String operation) throws IOException {
        if (response == null || !response.isSuccessful()) {
            int code = response == null ? 0 : response.code();
            if (code == 401 || code == 403) throw http(code);
            throw new ScanApiException(ScanApiException.Kind.HTTP, code, operation + "_HTTP_" + code);
        }
        if (response.body() == null) throw new ScanApiException(ScanApiException.Kind.INVALID_RESPONSE, 0, operation + "_EMPTY");
    }
    private static double similarity(String left, String right) {
        if (left.equals(right)) return 1d; int max = Math.max(left.length(), right.length());
        return max == 0 ? 1d : 1d - ((double) levenshtein(left, right) / max);
    }
    private static int levenshtein(String a, String b) {
        int[] previous = new int[b.length() + 1], current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) previous[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous; previous = current; current = swap;
        }
        return previous[b.length()];
    }
    private static Match cached(String key) {
        synchronized (CACHE) {
            CachedMatch entry = CACHE.get(key); if (entry == null) return null;
            if (System.currentTimeMillis() - entry.storedAt > 10 * 60 * 1000L) { CACHE.remove(key); return null; }
            return copy(entry.match);
        }
    }
    private static void cache(String key, Match match) { synchronized (CACHE) { CACHE.put(key, new CachedMatch(copy(match))); } }
    private static Match copy(Match source) {
        Match value = new Match(); value.position = source.position; value.rawName = source.rawName; value.normalizedName = source.normalizedName;
        value.status = source.status; value.resolutionStatus = source.resolutionStatus; value.resolutionMethod = source.resolutionMethod;
        value.confidence = source.confidence; value.needsReview = source.needsReview;
        value.inciName = source.inciName; value.ingredientId = source.ingredientId; value.errorCode = source.errorCode;
        value.candidateIds = source.candidateIds == null ? new ArrayList<>() : new ArrayList<>(source.candidateIds); return value;
    }
    private static ScanIngredientApi.Ingredient validIngredient(ScanIngredientApi.Ingredient value) throws IOException {
        if (value == null || value.id == null || value.id <= 0 || key(value.inciName).isEmpty()) throw invalid(); return value;
    }
    private static ScanIngredientApi.Alias validAlias(ScanIngredientApi.Alias value) throws IOException {
        if (value == null || value.id == null || value.ingredientId == null || value.ingredientId <= 0 || key(value.aliasName).isEmpty()) throw invalid(); return value;
    }
    private static String key(String value) { return ScanIngredientKeyNormalizer.normalize(value); }
    private static String emptyToNull(String value) { return value == null || value.trim().isEmpty() ? null : value.trim(); }
    private static ScanApiException http(int code) { return new ScanApiException(code == 401 ? ScanApiException.Kind.AUTH : code == 403 ? ScanApiException.Kind.FORBIDDEN : ScanApiException.Kind.HTTP, code, "CATALOG_HTTP_" + code); }
    private static IOException invalid() { return new IOException("CATALOG_INVALID_RESPONSE"); }
}
