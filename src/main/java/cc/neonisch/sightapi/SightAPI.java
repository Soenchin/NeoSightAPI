package cc.neonisch.sightapi;

import cc.neonisch.sightapi.server.ApiServer;
import cc.neonisch.sightapi.server.RegionStorage;
import cc.neonisch.sightapi.server.SightEventSubscriber;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod("neosightapi")
public class SightAPI {
    public static final String MOD_ID = "neosightapi";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private ApiServer apiServer;

    public SightAPI(IEventBus modEventBus) {
        // Register on game event bus — fires on dedicated server AND single-player integrated server
        NeoForge.EVENT_BUS.register(this);
    }

    @net.neoforged.bus.api.SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        try {
            // Load region storage
            RegionStorage.load(FMLPaths.CONFIGDIR.get());
            LOGGER.info("SightAPI region storage loaded");

            // Launch HTTP server
            apiServer = new ApiServer();
            apiServer.start();
            LOGGER.info("SightAPI HTTP server listening on port 8345");

            // Register SSE event subscriber
            NeoForge.EVENT_BUS.register(new SightEventSubscriber());
            LOGGER.info("SightAPI SSE event subscriber registered");
        } catch (Exception e) {
            LOGGER.error("Failed to start SightAPI server", e);
        }
    }
}
