package cc.neonisch.sightapi.server;

import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class ApiServer {
    private static final Logger LOGGER = LoggerFactory.getLogger("SightAPI-HTTP");
    private static final int PORT = 8345;

    private final HttpServer server;
    private final ApiRouter router;

    public ApiServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.setExecutor(Executors.newFixedThreadPool(4));
        router = new ApiRouter();
        registerRoutes();
    }

    private void registerRoutes() {
        // ---- Server ----
        server.createContext("/api/server", router::handleServer);
        server.createContext("/api/server/mods", router::handleServerMods);

        // ---- Players (list + /{name}) ----
        server.createContext("/api/players", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if ("/api/players".equals(path)) {
                router.handlePlayerList(exchange);
            } else if (path.startsWith("/api/players/")) {
                String name = path.substring("/api/players/".length());
                router.handlePlayerInfo(exchange, name);
            } else {
                router.handlePlayerList(exchange);
            }
        });

        // ---- Worlds (list / /{dim} / /{dim}/time) ----
        server.createContext("/api/worlds", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if ("/api/worlds".equals(path)) {
                router.handleWorldList(exchange);
            } else if (path.endsWith("/time")) {
                String dim = path.substring("/api/worlds/".length(), path.length() - "/time".length());
                router.handleWorldTime(exchange, dim);
            } else {
                String dim = path.substring("/api/worlds/".length());
                router.handleWorldInfo(exchange, dim);
            }
        });

        // ---- Command ----
        server.createContext("/api/command", router::handleCommand);

        // ---- Events (SSE) ----
        server.createContext("/api/events", router::handleEventStream);

        // ---- Chat history ----
        server.createContext("/api/chat", router::handleChatHistory);

        // ---- Regions (list/create + /{id} delete) ----
        server.createContext("/api/regions", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            if ("/api/regions".equals(path)) {
                if ("POST".equals(method)) {
                    router.handleRegionCreate(exchange);
                } else {
                    router.handleRegionList(exchange);
                }
            } else if (path.startsWith("/api/regions/")) {
                String id = path.substring("/api/regions/".length());
                router.handleRegionDelete(exchange, id);
            } else {
                router.handleRegionList(exchange);
            }
        });
    }

    public void start() {
        server.start();
        LOGGER.info("SightAPI HTTP server listening on port {}", PORT);
    }

    public void stop() {
        server.stop(2);
        LOGGER.info("SightAPI HTTP server stopped");
    }
}