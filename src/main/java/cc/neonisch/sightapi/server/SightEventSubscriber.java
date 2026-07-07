package cc.neonisch.sightapi.server;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Subscribes to NeoForge events and pushes them as SSE broadcasts
 * via {@link MinecraftBridge}.
 */
public class SightEventSubscriber {

    // 200 ticks = 10 seconds (Minecraft runs at 20 TPS)
    private static final int METRICS_SAMPLE_INTERVAL = 200;
    private int tickCounter = 0;

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        MinecraftBridge.broadcastSSE("player_join",
                jsonObj("player", sp.getGameProfile().getName(),
                        "uuid", sp.getGameProfile().getId().toString()));
    }

    @SubscribeEvent
    public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        MinecraftBridge.broadcastSSE("player_leave",
                jsonObj("player", sp.getGameProfile().getName()));
    }

    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        Component msg = event.getSource().getLocalizedDeathMessage(sp);
        MinecraftBridge.broadcastSSE("player_death",
                jsonObj("player", sp.getGameProfile().getName(),
                        "message", msg != null ? msg.getString() : ""));
    }

    @SubscribeEvent
    public void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        String adv = event.getAdvancement().id().toString();
        MinecraftBridge.broadcastSSE("advancement",
                jsonObj("player", event.getEntity().getGameProfile().getName(),
                        "advancement", adv));
    }

    @SubscribeEvent
    public void onChat(ServerChatEvent event) {
        String player = event.getPlayer().getGameProfile().getName();
        String message = event.getMessage().getString();
        long now = System.currentTimeMillis();

        MinecraftBridge.ChatEntry entry = new MinecraftBridge.ChatEntry(player, message, now);
        MinecraftBridge.addChat(entry);
        MinecraftBridge.broadcastSSE("chat",
                jsonObj("player", player, "message", message, "timestamp", String.valueOf(now)));
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (++tickCounter < METRICS_SAMPLE_INTERVAL) return;
        tickCounter = 0;

        MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
        if (srv == null || !srv.isRunning()) return;

        float mspt = srv.getCurrentSmoothedTickTime();
        float tps  = Math.min(20.0f, 1000.0f / Math.max(mspt, 0.001f));
        int players = srv.getPlayerCount();

        MinecraftBridge.addMetric(
                new MinecraftBridge.MetricSample(System.currentTimeMillis(), tps, mspt, players));
    }

    // ---- mini JSON builder (zero-dependency) ----

    private static String jsonObj(String... kv) {
        if (kv.length % 2 != 0) throw new IllegalArgumentException("kv must be even");
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < kv.length; i += 2) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(esc(kv[i])).append("\":\"")
              .append(esc(kv[i + 1])).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}