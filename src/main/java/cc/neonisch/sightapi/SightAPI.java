package cc.neonisch.sightapi;

import cc.neonisch.sightapi.server.ApiServer;
import cc.neonisch.sightapi.server.SightEventSubscriber;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod("neosightapi")
public class SightAPI {
    public static final String MOD_ID = "neosightapi";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private ApiServer apiServer;

    public SightAPI(IEventBus modEventBus) {
        // 服务端专用：客户端加载时直接跳过
        if (FMLEnvironment.dist != Dist.DEDICATED_SERVER) {
            LOGGER.info("SightAPI is server-only, skipping client/data-gen load");
            return;
        }
        modEventBus.addListener(this::onCommonSetup);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            try {
                // Launch HTTP server
                apiServer = new ApiServer();
                apiServer.start();
                LOGGER.info("SightAPI HTTP server listening on port 8345");

                // Register SSE event subscriber on NeoForge event bus
                NeoForge.EVENT_BUS.register(new SightEventSubscriber());
                LOGGER.info("SightAPI SSE event subscriber registered");
            } catch (Exception e) {
                LOGGER.error("Failed to start SightAPI HTTP server", e);
            }
        });
    }
}