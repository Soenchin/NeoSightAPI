package cc.neonisch.sightapi.server;

import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Thread-safe bridge between JDK HttpServer worker threads and the Minecraft
 * main server thread. All MC API calls MUST go through {@link #runOnServer}.
 */
public final class MinecraftBridge {

    private static final Logger LOGGER = LoggerFactory.getLogger("SightAPI-Bridge");

    // ---- SSE connections (thread-safe) ----
    private static final List<SSEConnection> sseClients = new CopyOnWriteArrayList<>();

    // ---- Chat ring buffer (thread-safe, max 100 entries) ----
    private static final int MAX_CHAT = 100;
    private static final List<ChatEntry> chatLog = new CopyOnWriteArrayList<>();

    // ========================================================================
    // Thread bridge
    // ========================================================================

    /**
     * Execute a task on the Minecraft server thread and return its result.
     * Blocks the caller for up to 5 seconds.
     */
    public static <T> T runOnServer(Supplier<T> task) {
        MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
        if (srv == null || !srv.isRunning()) {
            throw new IllegalStateException("Minecraft server is not running");
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        srv.execute(() -> {
            try {
                future.complete(task.get());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Server-thread task failed or timed out", e);
        }
    }

    /**
     * Run a task on the server thread without blocking.
     */
    public static void runOnServerAsync(Runnable task) {
        MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
        if (srv == null || !srv.isRunning()) return;
        srv.execute(task);
    }

    // ========================================================================
    // SSE management
    // ========================================================================

    public static void registerSSE(SSEConnection conn) {
        sseClients.add(conn);
        LOGGER.debug("SSE client connected (total: {})", sseClients.size());
    }

    public static void removeSSE(SSEConnection conn) {
        sseClients.remove(conn);
    }

    /**
     * Push an SSE event to all connected clients.
     * Dead connections are pruned on-the-fly.
     */
    public static void broadcastSSE(String event, String data) {
        if (sseClients.isEmpty()) return;
        String payload = "event: " + event + "\ndata: " + data + "\n\n";
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        List<SSEConnection> dead = null;
        for (SSEConnection conn : sseClients) {
            try {
                synchronized (conn) {
                    OutputStream os = conn.out;
                    if (os != null) {
                        os.write(bytes);
                        os.flush();
                    }
                }
            } catch (IOException e) {
                if (dead == null) dead = new ArrayList<>();
                dead.add(conn);
            }
        }
        if (dead != null) {
            sseClients.removeAll(dead);
            LOGGER.debug("Pruned {} dead SSE clients", dead.size());
        }
    }

    // ========================================================================
    // Chat history
    // ========================================================================

    public static void addChat(ChatEntry entry) {
        if (chatLog.size() >= MAX_CHAT) {
            chatLog.remove(0);
        }
        chatLog.add(entry);
    }

    /** Return the most recent {@code limit} entries, newest last. */
    public static List<ChatEntry> getChatHistory(int limit) {
        int size = chatLog.size();
        int from = Math.max(0, size - limit);
        return new ArrayList<>(chatLog.subList(from, size));
    }

    // ========================================================================
    // Data records
    // ========================================================================

    public record SSEConnection(OutputStream out) {}

    public record ChatEntry(String player, String message, long epochMillis) {}
}