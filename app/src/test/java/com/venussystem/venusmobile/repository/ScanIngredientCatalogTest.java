package com.venussystem.venusmobile.repository;

import com.venussystem.venusmobile.repository.api.ScanIngredientApi;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.IOException;
import java.util.*;
import okhttp3.Request;
import okhttp3.ResponseBody;
import okio.Timeout;
import retrofit2.*;

public class ScanIngredientCatalogTest {
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
        assertEquals(8, api.calls); // 4 INCI + 3 alias lookups + 1 canonical alias lookup.
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
        int calls, http = 200; boolean networkFailure, invalidBody;
        public Call<Ingredient> ingredientByInci(String name) { return response(items.get(name.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT))); }
        public Call<Ingredient> ingredientById(long id) { return response(items.get("id:" + id)); }
        public Call<Alias> aliasByName(String name) { return response(aliases.get(name.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT))); }
        public Call<Page<Ingredient>> ingredients(int p, int s, String sort) { throw new UnsupportedOperationException(); }
        public Call<Page<Alias>> aliases(int p, int s, String sort) { throw new UnsupportedOperationException(); }
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
