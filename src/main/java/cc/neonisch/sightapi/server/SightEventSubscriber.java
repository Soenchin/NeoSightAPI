package cc.neonisch.sightapi.server;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Subscribes to NeoForge events and pushes them as SSE broadcasts
 * via {@link MinecraftBridge}.
 */
public class SightEventSubscriber {

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