package cc.neonisch.sightapi.server;

import com.sun.net.httpserver.HttpExchange;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cc.neonisch.sightapi.server.RegionStorage.Region;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ApiRouter {

    private static final Logger LOGGER = LoggerFactory.getLogger("SightAPI-Router");
    private static final String JSON = "application/json; charset=utf-8";
    private static final String SSE  = "text/event-stream; charset=utf-8";

    // ========================================================================
    // GET /api/server — 综合状态
    // ========================================================================
    public void handleServer(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) { send405(ex); return; }

        String json = MinecraftBridge.runOnServer(() -> {
            MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
            Runtime rt = Runtime.getRuntime();

            long usedMB  = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
            long maxMB   = rt.maxMemory() / 1024 / 1024;

            // Real TPS / MSPT from MinecraftServer tick profiling
            float mspt = srv.getCurrentSmoothedTickTime();
            float tps  = Math.min(20.0f, 1000.0f / Math.max(mspt, 0.001f));

            int playerCount = srv.getPlayerCount();
            int maxPlayers  = srv.getMaxPlayers();

            StringBuilder sb = new StringBuilder(256);
            sb.append("{");
            sb.append("\"version\":\"").append(esc(srv.getServerVersion())).append("\",");
            sb.append("\"motd\":\"").append(esc(srv.getMotd())).append("\",");
            sb.append("\"tps\":").append(tps).append(",");
            sb.append("\"mspt\":").append(mspt).append(",");
            sb.append("\"players\":").append(playerCount).append(",");
            sb.append("\"maxPlayers\":").append(maxPlayers).append(",");
            sb.append("\"memoryUsedMB\":").append(usedMB).append(",");
            sb.append("\"memoryMaxMB\":").append(maxMB).append(",");
            sb.append("\"difficulty\":\"").append(srv.getWorldData().getDifficulty().name()).append("\"");
            sb.append("}");
            return sb.toString();
        });

        respondJson(ex, json);
    }

    // ========================================================================
    // GET /api/server/mods — loaded mod list
    // ========================================================================
    public void handleServerMods(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) { send405(ex); return; }

        String json = MinecraftBridge.runOnServer(() -> {
            var mods = ModList.get().getMods();
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (var m : mods) {
                if (!first) sb.append(",");
                first = false;
                sb.append("{\"id\":\"").append(esc(m.getModId())).append("\",");
                sb.append("\"name\":\"").append(esc(m.getDisplayName())).append("\",");
                sb.append("\"version\":\"").append(esc(m.getVersion().toString())).append("\"}");
            }
            sb.append("]");
            return sb.toString();
        });

        respondJson(ex, json);
    }

    // ========================================================================
    // GET /api/players — online player list
    // ========================================================================
    public void handlePlayerList(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) { send405(ex); return; }

        String json = MinecraftBridge.runOnServer(() -> {
            MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
            List<ServerPlayer> players = srv.getPlayerList().getPlayers();
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (ServerPlayer p : players) {
                if (!first) sb.append(",");
                first = false;
                sb.append("{\"name\":\"").append(esc(p.getGameProfile().getName())).append("\",");
                sb.append("\"uuid\":\"").append(p.getGameProfile().getId()).append("\",");
                sb.append("\"ping\":").append(p.connection.latency()).append(",");
                sb.append("\"dimension\":\"").append(p.serverLevel().dimension().location()).append("\",");
                sb.append("\"gamemode\":\"").append(gameModeName(p)).append("\"}");
            }
            sb.append("]");
            return sb.toString();
        });

        respondJson(ex, json);
    }

    // ========================================================================
    // GET /api/players/{name} — player detail
    // ========================================================================
    public void handlePlayerInfo(HttpExchange ex, String name) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) { send405(ex); return; }
        if (name == null || name.isEmpty()) { send400(ex, "Player name required"); return; }

        String json = MinecraftBridge.runOnServer(() -> {
            ServerPlayer p = findPlayer(name);
            if (p == null) return null;

            StringBuilder sb = new StringBuilder(256);
            sb.append("{");
            sb.append("\"name\":\"").append(esc(p.getGameProfile().getName())).append("\",");
            sb.append("\"uuid\":\"").append(p.getGameProfile().getId()).append("\",");
            sb.append("\"x\":").append(p.getX()).append(",");
            sb.append("\"y\":").append(p.getY()).append(",");
            sb.append("\"z\":").append(p.getZ()).append(",");
            sb.append("\"health\":").append(p.getHealth()).append(",");
            sb.append("\"maxHealth\":").append(p.getMaxHealth()).append(",");
            sb.append("\"food\":").append(p.getFoodData().getFoodLevel()).append(",");
            sb.append("\"saturation\":").append(p.getFoodData().getSaturationLevel()).append(",");
            sb.append("\"xpLevel\":").append(p.experienceLevel).append(",");
            sb.append("\"dimension\":\"").append(p.serverLevel().dimension().location()).append("\",");
            sb.append("\"gamemode\":\"").append(gameModeName(p)).append("\",");
            sb.append("\"ping\":").append(p.connection.latency());
            sb.append("}");
            return sb.toString();
        });

        if (json == null) { send404(ex, "Player not online: " + name); return; }
        respondJson(ex, json);
    }

    // ========================================================================
    // GET /api/worlds — dimension list
    // ========================================================================
    public void handleWorldList(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) { send405(ex); return; }

        String json = MinecraftBridge.runOnServer(() -> {
            MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (ServerLevel level : srv.getAllLevels()) {
                if (!first) sb.append(",");
                first = false;
                int playerCount = level.players().size();
                sb.append("{\"id\":\"").append(level.dimension().location()).append("\",");
                sb.append("\"players\":").append(playerCount).append(",");
                sb.append("\"dayTime\":").append(level.getDayTime()).append(",");
                sb.append("\"raining\":").append(level.isRaining()).append(",");
                sb.append("\"thundering\":").append(level.isThundering()).append("}");
            }
            sb.append("]");
            return sb.toString();
        });

        respondJson(ex, json);
    }

    // ========================================================================
    // GET /api/worlds/{dim} — dimension detail
    // ========================================================================
    public void handleWorldInfo(HttpExchange ex, String dim) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) { send405(ex); return; }
        if (dim == null || dim.isEmpty()) { send400(ex, "Dimension ID required"); return; }

        String json = MinecraftBridge.runOnServer(() -> {
            ServerLevel level = findLevel(dim);
            if (level == null) return null;

            StringBuilder sb = new StringBuilder(256);
            sb.append("{");
            sb.append("\"id\":\"").append(level.dimension().location()).append("\",");
            sb.append("\"dayTime\":").append(level.getDayTime()).append(",");
            sb.append("\"gameTime\":").append(level.getGameTime()).append(",");
            sb.append("\"raining\":").append(level.isRaining()).append(",");
            sb.append("\"thundering\":").append(level.isThundering()).append(",");
            sb.append("\"loadedChunks\":").append(level.getChunkSource().getLoadedChunksCount()).append(",");
            sb.append("\"players\":").append(level.players().size()).append(",");
            sb.append("\"seed\":").append(level.getSeed()).append(",");
            sb.append("\"difficulty\":\"").append(level.getDifficulty().name()).append("\"");
            sb.append("}");
            return sb.toString();
        });

        if (json == null) { send404(ex, "Dimension not found: " + dim); return; }
        respondJson(ex, json);
    }

    // ========================================================================
    // POST /api/worlds/{dim}/time — set time
    // ========================================================================
    public void handleWorldTime(HttpExchange ex, String dim) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) { send405(ex); return; }
        if (dim == null || dim.isEmpty()) { send400(ex, "Dimension ID required"); return; }

        String body = readBody(ex);
        String timeVal = parseJsonField(body, "time");
        if (timeVal == null) { send400(ex, "Field 'time' required"); return; }

        String json = MinecraftBridge.runOnServer(() -> {
            ServerLevel level = findLevel(dim);
            if (level == null) return null;

            long ticks = resolveTimeAlias(timeVal);
            if (ticks < 0) {
                try { ticks = Long.parseLong(timeVal); } catch (NumberFormatException e) { return "BAD_TIME"; }
            }
            level.setDayTime(ticks);

            StringBuilder sb = new StringBuilder(128);
            sb.append("{\"success\":true,");
            sb.append("\"dimension\":\"").append(esc(dim)).append("\",");
            sb.append("\"dayTime\":").append(ticks).append("}");
            return sb.toString();
        });

        if (json == null) { send404(ex, "Dimension not found: " + dim); return; }
        if ("BAD_TIME".equals(json)) { send400(ex, "Invalid time value: " + timeVal); return; }
        respondJson(ex, json);
    }

    /** Resolve time string to tick value. Returns -1 if not a known alias. */
    private static long resolveTimeAlias(String s) {
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "day", "morning"  -> 1000;
            case "noon", "midday"  -> 6000;
            case "sunset"          -> 12000;
            case "night"           -> 13000;
            case "midnight"        -> 18000;
            case "sunrise"         -> 23000;
            default                -> -1;
        };
    }

    // ========================================================================
    // POST /api/command — execute console command
    // ========================================================================
    public void handleCommand(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) { send405(ex); return; }

        String body = readBody(ex);
        String command = parseJsonField(body, "command");
        if (command == null || command.isBlank()) { send400(ex, "Field 'command' required"); return; }

        String json = MinecraftBridge.runOnServer(() -> {
            MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
            // performPrefixedCommand returns void in NeoForge 1.21.1
            srv.getCommands().performPrefixedCommand(
                    srv.createCommandSourceStack(), command);
            StringBuilder sb = new StringBuilder(128);
            sb.append("{\"success\":true,");
            sb.append("\"command\":\"").append(esc(command)).append("\"}");
            return sb.toString();
        });

        respondJson(ex, json);
    }

    // ========================================================================
    // GET /api/events — SSE stream
    // ========================================================================
    public void handleEventStream(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) { send405(ex); return; }

        ex.getResponseHeaders().set("Content-Type", SSE);
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        ex.getResponseHeaders().set("Connection", "keep-alive");
        ex.sendResponseHeaders(200, 0);

        MinecraftBridge.SSEConnection conn = new MinecraftBridge.SSEConnection(ex.getResponseBody());
        MinecraftBridge.registerSSE(conn);
        try {
            // Send initial connected event
            byte[] init = "event: connected\ndata: {}\n\n".getBytes(StandardCharsets.UTF_8);
            ex.getResponseBody().write(init);
            ex.getResponseBody().flush();

            // Keep connection alive — events are pushed by SightEventSubscriber
            // Block until client disconnects or server shuts down
            while (!Thread.currentThread().isInterrupted()) {
                Thread.sleep(30_000);
                // heartbeat ping
                ex.getResponseBody().write(": ping\n\n".getBytes(StandardCharsets.UTF_8));
                ex.getResponseBody().flush();
            }
        } catch (InterruptedException | IOException e) {
            // client disconnected — normal
        } finally {
            MinecraftBridge.removeSSE(conn);
        }
    }

    // ========================================================================
    // GET /api/chat?limit=N — recent chat history
    // ========================================================================
    public void handleChatHistory(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) { send405(ex); return; }

        String query = ex.getRequestURI().getQuery();
        int limit = 20;
        if (query != null) {
            for (String param : query.split("&")) {
                if (param.startsWith("limit=")) {
                    try { limit = Integer.parseInt(param.substring(6)); } catch (NumberFormatException ignored) {}
                }
            }
        }
        limit = Math.min(limit, 100);

        List<MinecraftBridge.ChatEntry> entries = MinecraftBridge.getChatHistory(limit);
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (MinecraftBridge.ChatEntry e : entries) {
            if (!first) sb.append(",");
            first = false;
            sb.append("{\"player\":\"").append(esc(e.player())).append("\",");
            sb.append("\"message\":\"").append(esc(e.message())).append("\",");
            sb.append("\"timestamp\":").append(e.epochMillis()).append("}");
        }
        sb.append("]");
        respondJson(ex, sb.toString());
    }

    // ========================================================================
    // POST /api/regions — create region
    // ========================================================================
    public void handleRegionCreate(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) { send405(ex); return; }

        RegionStorage storage = RegionStorage.getInstance();
        if (storage == null) { send(ex, 503, "{\"error\":\"Region storage not available\"}"); return; }

        String body = readBody(ex);
        String name      = parseJsonField(body, "name");
        String owner     = parseJsonField(body, "owner");
        String dimension = parseJsonField(body, "dimension");
        if (name == null || name.isEmpty())      { send400(ex, "Field 'name' required"); return; }
        if (owner == null || owner.isEmpty())     { send400(ex, "Field 'owner' required"); return; }
        if (dimension == null || dimension.isEmpty()) { send400(ex, "Field 'dimension' required"); return; }

        int x1 = parseIntField(body, "x1", 0);
        int z1 = parseIntField(body, "z1", 0);
        int x2 = parseIntField(body, "x2", 0);
        int z2 = parseIntField(body, "z2", 0);
        String label = parseJsonField(body, "label");

        Region r = storage.create(name, owner, dimension, x1, z1, x2, z2, label);

        byte[] bytes = r.toJson().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", JSON);
        ex.sendResponseHeaders(201, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    // ========================================================================
    // GET /api/regions — list all, or GET /api/regions?x=X&z=Z&dim=DIM
    // ========================================================================
    public void handleRegionList(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) { send405(ex); return; }

        RegionStorage storage = RegionStorage.getInstance();
        if (storage == null) { send(ex, 503, "{\"error\":\"Region storage not available\"}"); return; }

        // Check for coordinate query params
        Map<String, String> params = parseQuery(ex.getRequestURI());
        String xParam = params.get("x");
        String zParam = params.get("z");
        String dimParam = params.get("dim");

        List<Region> results;
        if (xParam != null && zParam != null && dimParam != null) {
            try {
                int x = Integer.parseInt(xParam);
                int z = Integer.parseInt(zParam);
                results = storage.findAt(x, z, dimParam);
            } catch (NumberFormatException e) {
                send400(ex, "Invalid coordinate values"); return;
            }
        } else {
            results = storage.listAll();
        }

        StringBuilder sb = new StringBuilder(512);
        sb.append("[");
        boolean first = true;
        for (Region r : results) {
            if (!first) sb.append(",");
            first = false;
            sb.append(r.toJson());
        }
        sb.append("]");

        respondJson(ex, sb.toString());
    }

    // ========================================================================
    // DELETE /api/regions/{id} — delete region (with owner check)
    // ========================================================================
    public void handleRegionDelete(HttpExchange ex, String id) throws IOException {
        if (!"DELETE".equals(ex.getRequestMethod())) { send405(ex); return; }
        if (id == null || id.isEmpty()) { send400(ex, "Region ID required"); return; }

        RegionStorage storage = RegionStorage.getInstance();
        if (storage == null) { send(ex, 503, "{\"error\":\"Region storage not available\"}"); return; }

        String body = readBody(ex);
        String owner = parseJsonField(body, "owner");
        if (owner == null || owner.isEmpty()) { send400(ex, "Field 'owner' required"); return; }

        boolean deleted = storage.delete(id, owner);
        if (!deleted) {
            send404(ex, "Region not found or owner mismatch: " + id);
            return;
        }

        ex.sendResponseHeaders(204, -1);
        ex.getResponseBody().close();
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private static ServerPlayer findPlayer(String name) {
        MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            if (p.getGameProfile().getName().equalsIgnoreCase(name)) return p;
        }
        return null;
    }

    private static ServerLevel findLevel(String dim) {
        MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
        for (ServerLevel level : srv.getAllLevels()) {
            if (level.dimension().location().toString().equals(dim)) return level;
        }
        return null;
    }

    private static String gameModeName(ServerPlayer p) {
        GameType gt = p.gameMode.getGameModeForPlayer();
        return gt != null ? gt.getName() : "unknown";
    }

    private static String readBody(HttpExchange ex) throws IOException {
        try (InputStream is = ex.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Extract a single string field value from flat JSON: {"key":"val"} */
    private static String parseJsonField(String json, String field) {
        String search = "\"" + field + "\"";
        int i = json.indexOf(search);
        if (i < 0) return null;
        i = json.indexOf("\"", i + search.length());
        if (i < 0) return null;
        int j = json.indexOf("\"", i + 1);
        if (j < 0) return null;
        return json.substring(i + 1, j);
    }

    /** Extract an integer field value from JSON: {"key":123} */
    private static int parseIntField(String json, String field, int def) {
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

    /** Parse URI query parameters into a map. */
    private static Map<String, String> parseQuery(URI uri) {
        Map<String, String> map = new HashMap<>();
        String query = uri.getQuery();
        if (query == null || query.isEmpty()) return map;
        for (String param : query.split("&")) {
            int eq = param.indexOf('=');
            if (eq > 0) {
                map.put(param.substring(0, eq), param.substring(eq + 1));
            }
        }
        return map;
    }

    // ---- response helpers ----

    private void respondJson(HttpExchange ex, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", JSON);
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private void send405(HttpExchange ex) throws IOException {
        send(ex, 405, "{\"error\":\"Method not allowed\"}");
    }

    private void send400(HttpExchange ex, String msg) throws IOException {
        send(ex, 400, "{\"error\":\"" + esc(msg) + "\"}");
    }

    private void send404(HttpExchange ex, String msg) throws IOException {
        send(ex, 404, "{\"error\":\"" + esc(msg) + "\"}");
    }

    private void send(HttpExchange ex, int code, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", JSON);
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
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