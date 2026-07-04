package cc.neonisch.sightapi.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Persistent storage for player-claimed regions.
 * File: config/neosight/regions.json
 * Format: {"regions": [{...}, ...]}
 */
public final class RegionStorage {

    private static final Logger LOGGER = LoggerFactory.getLogger("SightAPI-Regions");
    private static volatile RegionStorage INSTANCE;

    private final Path filePath;
    private final List<Region> regions = new ArrayList<>();

    private RegionStorage(Path filePath) {
        this.filePath = filePath;
    }

    /**
     * Load (or create) the singleton instance. Call once at startup.
     */
    public static RegionStorage load(Path configDir) {
        Path dir = configDir.resolve("neosight");
        Path file = dir.resolve("regions.json");

        RegionStorage storage = new RegionStorage(file);

        try {
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            if (Files.exists(file)) {
                String raw = Files.readString(file, StandardCharsets.UTF_8);
                storage.regions.addAll(parseRegionsArray(raw));
                LOGGER.info("Loaded {} regions from {}", storage.regions.size(), file);
            } else {
                storage.save();
                LOGGER.info("Created empty regions file at {}", file);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load regions file", e);
        }

        INSTANCE = storage;
        return storage;
    }

    public static RegionStorage getInstance() {
        return INSTANCE;
    }

    // ─── CRUD ──────────────────────────────────────────────

    public synchronized Region create(String name, String owner, String dimension,
                                       int x1, int z1, int x2, int z2, String label) {
        Region r = new Region();
        r.id = UUID.randomUUID().toString().substring(0, 8);
        r.name = name;
        r.owner = owner;
        r.dimension = dimension;
        r.x1 = Math.min(x1, x2);
        r.z1 = Math.min(z1, z2);
        r.x2 = Math.max(x1, x2);
        r.z2 = Math.max(z1, z2);
        r.label = label != null ? label : "";
        r.createdAt = System.currentTimeMillis();

        regions.add(r);
        save();
        return r;
    }

    public synchronized boolean delete(String id, String owner) {
        boolean removed = regions.removeIf(r -> r.id.equals(id) && r.owner.equals(owner));
        if (removed) save();
        return removed;
    }

    public List<Region> listAll() {
        return List.copyOf(regions);
    }

    /**
     * Find all regions that contain the given XZ coordinate in the given dimension.
     */
    public List<Region> findAt(int x, int z, String dimension) {
        List<Region> result = new ArrayList<>();
        for (Region r : regions) {
            if (r.dimension.equals(dimension)
                    && x >= r.x1 && x <= r.x2
                    && z >= r.z1 && z <= r.z2) {
                result.add(r);
            }
        }
        return result;
    }

    public List<Region> findByOwner(String owner) {
        List<Region> result = new ArrayList<>();
        for (Region r : regions) {
            if (r.owner.equals(owner)) result.add(r);
        }
        return result;
    }

    // ─── Persistence ───────────────────────────────────────

    private void save() {
        try {
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, toJson(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("Failed to save regions file", e);
        }
    }

    private String toJson() {
        StringBuilder sb = new StringBuilder(512);
        sb.append("{\"regions\":[");
        boolean first = true;
        for (Region r : regions) {
            if (!first) sb.append(",");
            first = false;
            sb.append(r.toJson());
        }
        sb.append("]}");
        return sb.toString();
    }

    // ─── JSON parsing (manual, zero dependencies) ──────────

    private static List<Region> parseRegionsArray(String json) {
        List<Region> result = new ArrayList<>();
        // Find the "regions" array content
        int arrStart = json.indexOf("[");
        int arrEnd = json.lastIndexOf("]");
        if (arrStart < 0 || arrEnd <= arrStart) return result;

        String inner = json.substring(arrStart + 1, arrEnd).trim();
        if (inner.isEmpty()) return result;

        // Split by top-level { ... } blocks
        int depth = 0;
        int blockStart = -1;
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (c == '{') {
                if (depth == 0) blockStart = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && blockStart >= 0) {
                    String block = inner.substring(blockStart, i + 1);
                    Region r = Region.fromJson(block);
                    if (r != null) result.add(r);
                    blockStart = -1;
                }
            }
        }
        return result;
    }

    // ─── Region data class ─────────────────────────────────

    public static final class Region {
        public String id;
        public String name;
        public String owner;
        public String dimension;
        public int x1, z1, x2, z2;
        public String label;
        public long createdAt;

        public String toJson() {
            StringBuilder sb = new StringBuilder(256);
            sb.append("{");
            sb.append("\"id\":\"").append(esc(id)).append("\",");
            sb.append("\"name\":\"").append(esc(name)).append("\",");
            sb.append("\"owner\":\"").append(esc(owner)).append("\",");
            sb.append("\"dimension\":\"").append(esc(dimension)).append("\",");
            sb.append("\"x1\":").append(x1).append(",");
            sb.append("\"z1\":").append(z1).append(",");
            sb.append("\"x2\":").append(x2).append(",");
            sb.append("\"z2\":").append(z2).append(",");
            sb.append("\"label\":\"").append(esc(label)).append("\",");
            sb.append("\"createdAt\":").append(createdAt);
            sb.append("}");
            return sb.toString();
        }

        public static Region fromJson(String json) {
            Region r = new Region();
            r.id        = parseStr(json, "id");
            r.name      = parseStr(json, "name");
            r.owner     = parseStr(json, "owner");
            r.dimension = parseStr(json, "dimension");
            r.label     = parseStr(json, "label");
            r.x1        = parseInt(json, "x1", 0);
            r.z1        = parseInt(json, "z1", 0);
            r.x2        = parseInt(json, "x2", 0);
            r.z2        = parseInt(json, "z2", 0);
            r.createdAt = parseLong(json, "createdAt", 0);
            if (r.id == null || r.name == null) return null;
            return r;
        }
    }

    // ─── JSON field helpers ────────────────────────────────

    private static String parseStr(String json, String field) {
        String search = "\"" + field + "\"";
        int i = json.indexOf(search);
        if (i < 0) return null;
        i = json.indexOf("\"", i + search.length());
        if (i < 0) return null;
        int j = json.indexOf("\"", i + 1);
        if (j < 0) return null;
        return json.substring(i + 1, j);
    }

    private static int parseInt(String json, String field, int def) {
        String search = "\"" + field + "\"";
        int i = json.indexOf(search);
        if (i < 0) return def;
        i = json.indexOf(":", i + search.length());
        if (i < 0) return def;
        i++;
        while (i < json.length() && json.charAt(i) == ' ') i++;
        int j = i;
        while (j < json.length() && (Character.isDigit(json.charAt(j)) || json.charAt(j) == '-')) j++;
        if (j == i) return def;
        try { return Integer.parseInt(json.substring(i, j)); } catch (NumberFormatException e) { return def; }
    }

    private static long parseLong(String json, String field, long def) {
        String search = "\"" + field + "\"";
        int i = json.indexOf(search);
        if (i < 0) return def;
        i = json.indexOf(":", i + search.length());
        if (i < 0) return def;
        i++;
        while (i < json.length() && json.charAt(i) == ' ') i++;
        int j = i;
        while (j < json.length() && Character.isDigit(json.charAt(j))) j++;
        if (j == i) return def;
        try { return Long.parseLong(json.substring(i, j)); } catch (NumberFormatException e) { return def; }
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
