package com.venussystem.venusmobile.repository;

import com.venussystem.venusmobile.repository.api.ScanApiException;
import com.venussystem.venusmobile.repository.api.ScanIngredientApi;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import retrofit2.Response;

/** Bounded exact catalog lookup. It never downloads the complete PostgreSQL catalog. */
public final class ScanIngredientCatalog {
    public interface AccountCheck { void run() throws IOException; }
    private final ScanIngredientApi api;
    private final AccountCheck checkAccount;
    private static final long CACHE_TTL_MS = 10 * 60 * 1000L;
    private static final int CACHE_LIMIT = 256;
    private static final Map<String, CachedMatch> CACHE = new LinkedHashMap<String, CachedMatch>(32, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, CachedMatch> eldest) {
            return size() > CACHE_LIMIT;
        }
    };

    private static final class CachedMatch {
        final Match match;
        final long storedAt;
        CachedMatch(Match match) { this.match = match; this.storedAt = System.currentTimeMillis(); }
    }

    public ScanIngredientCatalog(ScanIngredientApi api, AccountCheck checkAccount) {
        this.api = api;
        this.checkAccount = checkAccount;
    }

    public static final class Report {
        public String checkedAt;
        public List<Match> matches = new ArrayList<>();
        public int known, alias, ambiguous, notFound, errors;
    }

    public static final class Match {
        public String rawName, status, inciName;
        public Long ingredientId;
        public Integer errorCode;
        public List<Long> candidateIds = new ArrayList<>();
    }

    public Report lookup(List<String> rawNames) throws IOException {
        if (rawNames == null || rawNames.isEmpty() || rawNames.size() > 200)
            throw new IOException("Lista de ingredientes ausente ou fora do limite.");
        Report report = new Report();
        report.checkedAt = OffsetDateTime.now().toString();
        for (String raw : rawNames) {
            String normalizedKey = key(raw);
            if (normalizedKey.isEmpty() || raw.length() > 300) throw invalid();
            // Include the service instance so tests/tenants cannot leak a
            // result between different API bases while the app still reuses
            // the singleton Retrofit service across scans.
            String cacheKey = System.identityHashCode(api) + ":" + normalizedKey;
            checkAccount.run();
            Match cached = cached(cacheKey);
            if (cached != null) {
                cached.rawName = raw;
                append(report, cached);
                continue;
            }
            Match match = new Match();
            match.rawName = raw;
            Response<ScanIngredientApi.Ingredient> inci = api.ingredientByInci(raw.trim()).execute();
            checkAccount.run();
            if (inci.isSuccessful()) {
                ScanIngredientApi.Ingredient body = validIngredient(inci.body());
                match.status = "KNOWN";
                match.ingredientId = body.id;
                match.inciName = body.inciName;
                cache(cacheKey, match);
                append(report, match);
                continue;
            }
            if (inci.code() != 404 && inci.code() != 400) {
                if (inci.code() == 401 || inci.code() == 403) throw http(inci.code());
                match.status = "LOOKUP_ERROR";
                match.errorCode = inci.code();
                append(report, match);
                continue;
            }

            Response<ScanIngredientApi.Alias> alias = api.aliasByName(raw.trim()).execute();
            checkAccount.run();
            if (alias.isSuccessful()) {
                ScanIngredientApi.Alias aliasBody = validAlias(alias.body());
                Response<ScanIngredientApi.Ingredient> canonical = api.ingredientById(aliasBody.ingredientId).execute();
                checkAccount.run();
                if (!canonical.isSuccessful()) {
                    if (canonical.code() == 401 || canonical.code() == 403) throw http(canonical.code());
                    match.status = "LOOKUP_ERROR";
                    match.errorCode = canonical.code();
                    report.errors++;
                    report.matches.add(match);
                    continue;
                }
                ScanIngredientApi.Ingredient body = validIngredient(canonical.body());
                match.status = "ALIAS";
                match.ingredientId = body.id;
                match.inciName = body.inciName;
                cache(cacheKey, match);
            } else if (alias.code() == 404 || alias.code() == 400) {
                match.status = "NOT_FOUND";
                cache(cacheKey, match);
            } else {
                if (alias.code() == 401 || alias.code() == 403) throw http(alias.code());
                match.status = "LOOKUP_ERROR";
                match.errorCode = alias.code();
            }
            append(report, match);
        }
        checkAccount.run();
        return report;
    }

    private static void append(Report report, Match match) {
        report.matches.add(match);
        if ("KNOWN".equals(match.status)) report.known++;
        else if ("ALIAS".equals(match.status)) report.alias++;
        else if ("AMBIGUOUS".equals(match.status)) report.ambiguous++;
        else if ("NOT_FOUND".equals(match.status)) report.notFound++;
        else if ("LOOKUP_ERROR".equals(match.status)) report.errors++;
    }

    private static Match cached(String key) {
        synchronized (CACHE) {
            CachedMatch entry = CACHE.get(key);
            if (entry == null) return null;
            if (System.currentTimeMillis() - entry.storedAt > CACHE_TTL_MS) {
                CACHE.remove(key);
                return null;
            }
            return copy(entry.match);
        }
    }

    private static void cache(String key, Match match) {
        synchronized (CACHE) { CACHE.put(key, new CachedMatch(copy(match))); }
    }

    private static Match copy(Match source) {
        Match copy = new Match();
        copy.rawName = source.rawName;
        copy.status = source.status;
        copy.inciName = source.inciName;
        copy.ingredientId = source.ingredientId;
        copy.errorCode = source.errorCode;
        copy.candidateIds = source.candidateIds == null
                ? new ArrayList<>() : new ArrayList<>(source.candidateIds);
        return copy;
    }

    private static ScanIngredientApi.Ingredient validIngredient(ScanIngredientApi.Ingredient value)
            throws IOException {
        if (value == null || value.id == null || value.id <= 0 || key(value.inciName).isEmpty()) throw invalid();
        return value;
    }
    private static ScanIngredientApi.Alias validAlias(ScanIngredientApi.Alias value) throws IOException {
        if (value == null || value.id == null || value.ingredientId == null || value.ingredientId <= 0
                || key(value.aliasName).isEmpty()) throw invalid();
        return value;
    }
    private static String key(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }
    private static ScanApiException http(int code) {
        return new ScanApiException(code == 401 ? ScanApiException.Kind.AUTH
                : code == 403 ? ScanApiException.Kind.FORBIDDEN : ScanApiException.Kind.HTTP,
                code, "CATALOG_HTTP_" + code);
    }
    private static IOException invalid() { return new IOException("CATALOG_INVALID_RESPONSE"); }
}
