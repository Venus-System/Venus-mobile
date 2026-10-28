package com.venussystem.venusmobile.repository;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public final class ScanIngredientCatalogCache {
    public static final int SCHEMA_VERSION = 1;
    private static final Gson GSON = new Gson();
    private final Path file;

    public ScanIngredientCatalogCache(Path file) {
        this.file = file.toAbsolutePath().normalize();
    }

    public Snapshot read() throws IOException {
        if (!Files.isRegularFile(file)) return null;
        try {
            Snapshot snapshot = GSON.fromJson(new String(Files.readAllBytes(file), StandardCharsets.UTF_8), Snapshot.class);
            if (snapshot == null || snapshot.schemaVersion != SCHEMA_VERSION
                    || snapshot.updatedAtEpochMs <= 0 || snapshot.ingredients == null
                    || snapshot.aliases == null) return null;
            return snapshot;
        } catch (RuntimeException e) {
            throw new IOException("CATALOG_CACHE_INVALID", e);
        }
    }

    public Snapshot readFresh(long ttlMs) throws IOException {
        Snapshot snapshot = read();
        if (snapshot == null || System.currentTimeMillis() - snapshot.updatedAtEpochMs > ttlMs) return null;
        return snapshot;
    }

    public void write(Snapshot snapshot) throws IOException {
        if (snapshot == null) throw new IOException("CATALOG_CACHE_EMPTY");
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "catalog-", ".tmp");
        try {
            Files.write(temporary, GSON.toJson(snapshot).getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static Snapshot snapshot(List<IngredientEntry> ingredients, List<AliasEntry> aliases) {
        Snapshot snapshot = new Snapshot();
        snapshot.schemaVersion = SCHEMA_VERSION;
        snapshot.updatedAtEpochMs = System.currentTimeMillis();
        snapshot.ingredients = ingredients == null ? new ArrayList<>() : new ArrayList<>(ingredients);
        snapshot.aliases = aliases == null ? new ArrayList<>() : new ArrayList<>(aliases);
        return snapshot;
    }

    public static final class Snapshot {
        public int schemaVersion;
        public long updatedAtEpochMs;
        public List<IngredientEntry> ingredients = new ArrayList<>();
        public List<AliasEntry> aliases = new ArrayList<>();
    }

    public static final class IngredientEntry {
        public Long id;
        public String inciName;
        public String commonName;

        public IngredientEntry() { }

        public IngredientEntry(Long id, String inciName, String commonName) {
            this.id = id;
            this.inciName = inciName;
            this.commonName = commonName;
        }
    }

    public static final class AliasEntry {
        public Long id;
        public Long ingredientId;
        public String aliasName;

        public AliasEntry() { }

        public AliasEntry(Long id, Long ingredientId, String aliasName) {
            this.id = id;
            this.ingredientId = ingredientId;
            this.aliasName = aliasName;
        }
    }
}
