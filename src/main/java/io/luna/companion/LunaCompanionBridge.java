package io.luna.companion;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import engine.trade.ConfirmTradeInterface;
import engine.trade.OfferTradeInterface;
import io.luna.LunaContext;
import io.luna.game.model.item.ItemContainer;
import io.luna.game.model.mob.Player;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Optional loopback-only, read-only inspection bridge for Luna Companion.
 * All world snapshots are built on the game thread and contain only explicit DTO fields.
 *
 * @author CJ
 */
public final class LunaCompanionBridge {
    private static final Gson JSON = new Gson();

    private LunaCompanionBridge() {
    }

    /** Starts only when LUNA_COMPANION_TOKEN is configured; default port is 8787. */
    public static void startIfConfigured(LunaContext context) throws IOException {
        String token = System.getenv("LUNA_COMPANION_TOKEN");
        if (token == null || token.isBlank()) {
            return;
        }
        String portText = System.getenv("LUNA_COMPANION_PORT");
        int port = portText == null ? 8787 : Integer.parseInt(portText);
        start(context, token, port);
    }

    /** Package-visible entrypoint for integration tests. */
    static HttpServer start(LunaContext context, String token, int port) throws IOException {
        if (token.length() < 32 || !token.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("LUNA_COMPANION_TOKEN must have at least 32 URL-safe characters.");
        }
        if (port < 1024 || port > 65535) {
            throw new IllegalArgumentException("LUNA_COMPANION_PORT must be between 1024 and 65535.");
        }
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 8);
        var executor = Executors.newFixedThreadPool(2, runnable -> {
            var thread = new Thread(runnable, "LunaCompanionBridge");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.createContext("/", exchange -> handle(exchange, context, token));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(0);
            executor.shutdownNow();
        }, "LunaCompanionShutdown"));
        server.start();
        return server;
    }

    private static void handle(HttpExchange exchange, LunaContext context, String token) throws IOException {
        try {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "GET");
                respond(exchange, 405, Map.of("error", "Only GET is supported."));
                return;
            }
            if (exchange.getRequestHeaders().containsKey("Origin")) {
                respond(exchange, 403, Map.of("error", "Browser requests are not supported."));
                return;
            }
            String supplied = exchange.getRequestHeaders().getFirst("Authorization");
            if (supplied == null || !MessageDigest.isEqual(
                    ("Bearer " + token).getBytes(StandardCharsets.UTF_8),
                    supplied.getBytes(StandardCharsets.UTF_8))) {
                respond(exchange, 401, Map.of("error", "Authentication required."));
                return;
            }
            String path = exchange.getRequestURI().getPath();
            Supplier<Object> snapshot;
            switch (path) {
                case "/status" -> snapshot = () -> {
                    int players = 0;
                    int bots = 0;
                    for (Player player : context.getWorld().getPlayers()) {
                        if (player == null) continue;
                        players++;
                        if (player.isBot()) bots++;
                    }
                    return Map.of("tick", context.getWorld().getCurrentTick(),
                            "gameService", context.getGame().state().name(),
                            "online", players, "bots", bots, "humans", players - bots);
                };
                case "/players" -> snapshot = () -> {
                    var result = new ArrayList<Map<String, Object>>();
                    for (Player player : context.getWorld().getPlayers()) {
                        if (player == null) continue;
                        result.add(Map.of("username", player.getUsername(), "bot", player.isBot()));
                    }
                    return Map.of("tick", context.getWorld().getCurrentTick(), "players", result);
                };
                case "/trade" -> {
                    String username = username(exchange.getRequestURI().getRawQuery());
                    snapshot = () -> context.getWorld().getPlayer(username)
                            .<Object>map(player -> trade(context, player))
                            .orElseGet(() -> Map.of("error", "Player is not online."));
                }
                default -> {
                    respond(exchange, 404, Map.of("error", "Unknown endpoint."));
                    return;
                }
            }
            // Never wait on the game thread. This handler runs on the dedicated bridge executor.
            Object result = context.getGame().sync(snapshot).get(3, TimeUnit.SECONDS);
            respond(exchange, 200, result);
        } catch (IllegalArgumentException e) {
            respond(exchange, 400, Map.of("error", "A username of 1–12 characters is required."));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            respond(exchange, 503, Map.of("error", "Snapshot interrupted."));
        } catch (Exception e) {
            respond(exchange, 503, Map.of("error", "Game snapshot unavailable; retry when Luna is running."));
        } finally {
            exchange.close();
        }
    }

    private static String username(String query) {
        if (query != null) {
            for (String pair : query.split("&")) {
                String[] parts = pair.split("=", 2);
                if (parts.length == 2 && parts[0].equals("username")) {
                    String value = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
                    if (!value.isBlank() && value.length() <= 12) return value;
                }
            }
        }
        throw new IllegalArgumentException("Missing username.");
    }

    private static Object trade(LunaContext context, Player player) {
        var offer = player.getOverlays().getOverlay(OfferTradeInterface.class);
        var confirm = player.getOverlays().getOverlay(ConfirmTradeInterface.class);
        boolean confirming = confirm != null;
        if (confirming) offer = confirm.getOffer();
        var result = new LinkedHashMap<String, Object>();
        result.put("tick", context.getWorld().getCurrentTick());
        result.put("username", player.getUsername());
        result.put("stage", offer == null ? "none" : confirming ? "confirm" : "offer");
        if (offer == null) return result;
        Player other = offer.getOther();
        var opposing = other.getOverlays().getOverlay(OfferTradeInterface.class);
        var opposingConfirm = other.getOverlays().getOverlay(ConfirmTradeInterface.class);
        if (opposingConfirm != null) opposing = opposingConfirm.getOffer();
        result.put("other", other.getUsername());
        result.put("items", items(offer.getItems()));
        result.put("otherItems", opposing == null ? List.of() : items(opposing.getItems()));
        // These are first-screen acceptance flags, not second-screen confirmation flags.
        if (!confirming) {
            result.put("accepted", offer.getAccepted());
            result.put("otherAccepted", opposing != null && opposing.getAccepted());
        }
        result.put("valuationAvailable", false);
        return result;
    }

    private static List<Map<String, Object>> items(ItemContainer container) {
        var result = new ArrayList<Map<String, Object>>();
        for (var item : container) {
            if (item != null) result.add(Map.of("id", item.getId(), "amount", item.getAmount()));
        }
        return result;
    }

    private static void respond(HttpExchange exchange, int status, Object value) throws IOException {
        byte[] body = JSON.toJson(value).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }
}
