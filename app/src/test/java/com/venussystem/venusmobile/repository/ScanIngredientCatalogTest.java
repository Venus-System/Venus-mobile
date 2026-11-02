package com.venussystem.venusmobile.repository;

import com.venussystem.venusmobile.repository.api.ScanIngredientApi;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.IOException;
import java.util.*;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import okhttp3.Request;
import okhttp3.ResponseBody;
import okio.Timeout;
import retrofit2.http.GET;
import retrofit2.*;

public class ScanIngredientCatalogTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void ingredientCatalogueApiRemainsReadOnly() {
        for (java.lang.reflect.Method method : ScanIngredientApi.class.getDeclaredMethods()) {
            assertNotNull("Every catalogue operation must be a GET: " + method.getName(),
                    method.getAnnotation(GET.class));
        }
    }

    @Test public void snapshotResolvesInciAliasCommonNameAndFuzzyWithoutNPlusOne() throws Exception {
        Fake api = new Fake();
        ScanIngredientApi.Ingredient aqua = ingredient(2, "AQUA");
        aqua.commonName = "WATER";
        api.allIngredientsList.add(aqua);
        api.allIngredientsList.add(ingredient(3, "GLYCINE"));
        api.allAliasesList.add(alias(7, 3, "AMINOACETIC ACID"));
        ScanIngredientCatalog.Report report = new ScanIngredientCatalog(api, () -> {},
                new ScanIngredientCatalogCache(temporary.newFolder("catalog").toPath().resolve("catalog.json")))
                .lookup(Arrays.asList("AQUA", "WATER", "AMINOACETIC ACID", "QLYCINE", "UNKNOWN"));
        assertEquals(2, api.calls);
        assertEquals(5, report.matches.size());
        assertEquals("KNOWN", report.matches.get(0).status);
        assertEquals("COMMON_NAME", report.matches.get(1).status);
        assertEquals("ALIAS", report.matches.get(2).status);
        assertEquals(Long.valueOf(3), report.matches.get(3).ingredientId);
        assertEquals("NOT_FOUND", report.matches.get(4).status);
        assertEquals(4, report.resolved);
        assertEquals(1, report.unresolved);
    }

    @Test public void refreshesCorruptedSnapshotBeforeUsingCatalogue() throws Exception {
        Fake api = new Fake();
        api.allIngredientsList.add(ingredient(2, "AQUA"));
        java.nio.file.Path file = temporary.newFolder("broken-catalog").toPath().resolve("catalog.json");
        java.nio.file.Files.createDirectories(file.getParent());
        java.nio.file.Files.write(file, "not-json".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ScanIngredientCatalog.Report report = new ScanIngredientCatalog(api, () -> {},
                new ScanIngredientCatalogCache(file)).lookup(Collections.singletonList("AQUA"));
        assertEquals(2, api.calls);
        assertEquals(1, report.resolved);
        assertTrue(report.cacheFresh);
    }

    @Test public void reusesFreshSnapshotAcrossLookupsWithoutAnotherCatalogRequest() throws Exception {
        Fake api = new Fake();
        api.allIngredientsList.add(ingredient(2, "AQUA"));
        java.nio.file.Path file = temporary.newFolder("reusable-catalog").toPath().resolve("catalog.json");
        ScanIngredientCatalogCache cache = new ScanIngredientCatalogCache(file);

        ScanIngredientCatalog first = new ScanIngredientCatalog(api, () -> {}, cache);
        assertEquals("KNOWN", first.lookup(Collections.singletonList("AQUA")).matches.get(0).status);
        assertEquals(2, api.calls);

        ScanIngredientCatalog second = new ScanIngredientCatalog(api, () -> {}, cache);
        ScanIngredientCatalog.Report report = second.lookup(Collections.singletonList("AQUA"));
        assertEquals("KNOWN", report.matches.get(0).status);
        assertTrue(report.cacheFresh);
        assertEquals(2, api.calls);
    }

    @Test public void warmSnapshotPreloadsTheReadOnlyCatalogueForTheNextLookup() throws Exception {
        Fake api = new Fake();
        api.allIngredientsList.add(ingredient(2, "AQUA"));
        ScanIngredientCatalogCache cache = new ScanIngredientCatalogCache(
                temporary.newFolder("warm-catalog").toPath().resolve("catalog.json"));
        ScanIngredientCatalog catalog = new ScanIngredientCatalog(api, () -> {}, cache);

        catalog.warmSnapshot();

        assertEquals(2, api.calls);
        assertEquals("KNOWN", catalog.lookup(Collections.singletonList("AQUA"))
                .matches.get(0).status);
        assertEquals(2, api.calls);
    }

    @Test public void fallsBackToStaleSnapshotWhenRefreshIsUnavailable() throws Exception {
        Fake api = new Fake();
        api.networkFailure = true;
        java.nio.file.Path file = temporary.newFolder("stale-catalog").toPath().resolve("catalog.json");
        ScanIngredientCatalogCache cache = new ScanIngredientCatalogCache(file);
        ScanIngredientCatalogCache.Snapshot stale = ScanIngredientCatalogCache.snapshot(
                Collections.singletonList(new ScanIngredientCatalogCache.IngredientEntry(2L, "AQUA", "WATER")),
                Collections.emptyList());
        stale.updatedAtEpochMs = System.currentTimeMillis() - 3L * 24L * 60L * 60L * 1000L;
        cache.write(stale);
        ScanIngredientCatalog.Report report = new ScanIngredientCatalog(api, () -> {}, cache)
                .lookup(Collections.singletonList("WATER"));
        // Both read-only catalogue endpoints are attempted together; a stale
        // local snapshot still remains the fallback when refresh is offline.
        assertEquals(2, api.calls);
        assertEquals(1, report.resolved);
        assertFalse(report.cacheFresh);
    }

    @Test public void exactOfficialNamesAndAliasesAreFoundWithoutCatalogDownload() throws Exception {
        Fake api = new Fake();
        api.items.put("NOVEL OFFICIAL INGREDIENT", ingredient(1, "Novel Official Ingredient"));
        api.items.put("AQUA", ingredient(2, "Aqua"));
        api.items.put("id:2", ingredient(2, "Aqua"));
        api.aliases.put("WATER", alias(3, 2, "Water"));
        ScanIngredientCatalog.Report r = catalog(api).lookup(Arrays.asList(
                " novel official ingredient ", "WATER", "QLYCINE", "BROKEN MERGED NAME"));
        assertEquals(1, r.known); assertEquals(1, r.alias); assertEquals(2, r.notFound);
        assertEquals(" novel official ingredient ", r.matches.get(0).rawName);
        assertEquals(Long.valueOf(2), r.matches.get(1).ingredientId);
        assertEquals("NOT_FOUND", r.matches.get(2).status);
        assertEquals(8, api.calls);
    }
    @Test public void inciIsPreferredOverAliasAndAliasUsesCanonicalIngredient() throws Exception {
        Fake api = new Fake();
        api.items.put("GLYCINE", ingredient(3, "GLYCINE"));
        api.aliases.put("GLYCINE", alias(4, 1, "GLYCINE"));
        ScanIngredientCatalog.Report r = catalog(api).lookup(Arrays.asList("glycine"));
        assertEquals(1, r.known); assertEquals(0, r.alias);
        assertEquals(Long.valueOf(3), r.matches.get(0).ingredientId);
        assertEquals(1, api.calls);
    }

    @Test public void rexonaClinicalCleanMenOfficialCompositionResolvesAllSeventeenIds() throws Exception {
        Fake api = new Fake();
        String[] names = {
                "AQUA", "ALUMINUM SESQUICHLOROHYDRATE", "STEARYL ALCOHOL", "DIMETHICONE",
                "GLYCERIN", "GLYCERYL STEARATE", "PPG-15 STEARYL ETHER", "CALCIUM CHLORIDE",
                "STEARETH-20", "STEARETH-2",
                "PENTAERYTHRITYL TETRA-DI-T-BUTYL HYDROXYHYDROCINNAMATE",
                "GELATIN CROSSPOLYMER", "ALPHA-ISOMETHYL IONONE", "BENZYL SALICYLATE",
                "HEXYL CINNAMAL", "LIMONENE", "LINALOOL"
        };
        long[] ids = {
                514L, 366L, 7329L, 2125L, 2753L, 2809L, 5852L, 1047L, 7300L,
                7298L, 8030L, 8029L, 322L, 714L, 2989L, 3747L, 3748L
        };
        for (int i = 0; i < names.length; i++) {
            api.items.put(names[i], ingredient(ids[i], names[i]));
        }

        ScanIngredientCatalog.Report report = catalog(api).lookup(Arrays.asList(names));

        assertEquals(17, report.matches.size());
        assertEquals(17, report.known);
        assertEquals(0, report.alias);
        assertEquals(0, report.fuzzy);
        assertEquals(0, report.ambiguous);
        assertEquals(0, report.notFound);
        assertEquals(0, report.errors);
        assertEquals(17, report.resolved);
        assertEquals(0, report.unresolved);
        assertEquals("VALID", report.validationStatus);
        for (int i = 0; i < ids.length; i++) {
            assertEquals(Long.valueOf(ids[i]), report.matches.get(i).ingredientId);
            assertEquals("KNOWN", report.matches.get(i).status);
        }
    }
    @Test public void duplicateNamesReuseOneCatalogLookup() throws Exception {
        Fake api = new Fake();
        api.items.put("AQUA", ingredient(2, "AQUA"));
        ScanIngredientCatalog.Report r = catalog(api).lookup(Arrays.asList("AQUA", " aqua ", "AQUA"));
        assertEquals(3, r.matches.size());
        assertEquals(3, r.known);
        assertEquals(1, api.calls);
    }
    @Test public void aliasResolutionIsExplicitAndNotAmbiguousLocally() throws Exception {
        Fake api = new Fake();
        api.aliases.put("WATER", alias(1, 9, "Water"));
        api.items.put("id:9", ingredient(9, "AQUA"));
        ScanIngredientCatalog.Report r = catalog(api).lookup(Arrays.asList("WATER"));
        assertEquals(1, r.alias); assertEquals(Long.valueOf(9), r.matches.get(0).ingredientId);
        assertEquals("AQUA", r.matches.get(0).inciName);
    }
    @Test public void notFoundAndHttpFailuresAreDistinguished() throws Exception {
        Fake api = new Fake();
        ScanIngredientCatalog.Report r = catalog(api).lookup(Arrays.asList("UNKNOWN"));
        assertEquals(1, r.notFound);
        for (int code : new int[] {401, 403}) {
            Fake failing = new Fake(); failing.http = code;
            IOException e = assertThrows(IOException.class, () -> catalog(failing).lookup(Arrays.asList("AQUA")));
            assertTrue(e.getMessage().contains(String.valueOf(code)));
        }
        for (int code : new int[] {429, 500}) {
            Fake failing = new Fake(); failing.http = code;
            ScanIngredientCatalog.Report partial = catalog(failing).lookup(Arrays.asList("AQUA"));
            assertEquals(1, partial.errors);
            assertEquals("LOOKUP_ERROR", partial.matches.get(0).status);
        }
    }
    @Test public void networkFailureIsNotNotFound() {
        Fake api = new Fake(); api.networkFailure = true;
        assertThrows(IOException.class, () -> catalog(api).lookup(Arrays.asList("AQUA")));
    }
    @Test public void accountChangeDiscardsResponse() {
        Fake api = new Fake(); int[] checks = {0};
        ScanIngredientCatalog c = new ScanIngredientCatalog(api, () -> {
            if (++checks[0] == 2) throw new IOException("ACCOUNT_CHANGED");
        });
        assertThrows(IOException.class, () -> c.lookup(Arrays.asList("AQUA")));
        assertEquals(1, api.calls);
    }
    @Test public void invalidResponsesAndEmptyCandidatesAreRejected() {
        Fake invalid = new Fake(); invalid.invalidBody = true;
        invalid.items.put("AQUA", ingredient(1, "AQUA"));
        assertThrows(IOException.class, () -> catalog(invalid).lookup(Arrays.asList("AQUA")));
        Fake empty = new Fake();
        assertThrows(IOException.class, () -> catalog(empty).lookup(Collections.emptyList()));
        assertEquals(0, empty.calls);
    }
    private static ScanIngredientCatalog catalog(Fake api) { return new ScanIngredientCatalog(api, () -> {}); }
    private static ScanIngredientApi.Ingredient ingredient(long id, String name) {
        ScanIngredientApi.Ingredient i = new ScanIngredientApi.Ingredient(); i.id = id; i.inciName = name; return i;
    }
    private static ScanIngredientApi.Alias alias(long id, long ingredient, String name) {
        ScanIngredientApi.Alias a = new ScanIngredientApi.Alias(); a.id = id; a.ingredientId = ingredient; a.aliasName = name; return a;
    }
    private static class Fake implements ScanIngredientApi {
        Map<String, Ingredient> items = new HashMap<>(); Map<String, Alias> aliases = new HashMap<>();
        List<Ingredient> allIngredientsList = new ArrayList<>(); List<Alias> allAliasesList = new ArrayList<>();
        int calls, http = 200; boolean networkFailure, invalidBody;
        public Call<Ingredient> ingredientByInci(String name) { return response(items.get(name.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT))); }
        public Call<List<Ingredient>> allIngredients() { return response(allIngredientsList); }
        public Call<Ingredient> ingredientById(long id) { return response(items.get("id:" + id)); }
        public Call<Alias> aliasByName(String name) { return response(aliases.get(name.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT))); }
        public Call<Page<Ingredient>> ingredients(int p, int s, String sort) { throw new UnsupportedOperationException(); }
        public Call<Page<Alias>> aliases(int p, int s, String sort) { throw new UnsupportedOperationException(); }
        public Call<List<Alias>> allAliases() { return response(allAliasesList); }
        private <T> Call<T> response(T value) {
            calls++;
            return new Call<T>() {
                public Response<T> execute() throws IOException {
                    if (networkFailure) throw new IOException("offline");
                    if (http != 200) return Response.error(http, ResponseBody.create(null, "{}"));
                    if (value == null) return Response.error(404, ResponseBody.create(null, "{}"));
                    if (invalidBody) return Response.success((T) new ScanIngredientApi.Ingredient());
                    return Response.success(value);
                }
                public void enqueue(Callback<T> c) { throw new UnsupportedOperationException(); }
                public boolean isExecuted() { return false; } public void cancel() { }
                public boolean isCanceled() { return false; } public Call<T> clone() { throw new UnsupportedOperationException(); }
                public Request request() { throw new UnsupportedOperationException(); } public Timeout timeout() { return Timeout.NONE; }
            };
        }
    }
}
