package io.luna.companion;

import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import engine.trade.ConfirmTradeInterface;
import engine.trade.OfferTradeInterface;
import io.luna.LunaContext;
import io.luna.game.GameService;
import io.luna.game.model.World;
import io.luna.game.model.item.Item;
import io.luna.game.model.item.ItemContainer;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.overlay.AbstractOverlaySet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Exercises actual HTTP handlers with a fixture world; does not start a full game server.
 *
 * @author CJ
 */
class LunaCompanionBridgeTest {
    private static final String TOKEN = "fixture-token-with-at-least-32-characters";
    private final HttpClient client = HttpClient.newHttpClient();
    private HttpServer server;
    private LunaContext context;
    private World world;
    private Player player;
    private AbstractOverlaySet overlays;

    @BeforeEach
    void setUp() throws Exception {
        context = mock(LunaContext.class);
        world = mock(World.class);
        var game = mock(GameService.class);
        player = mock(Player.class);
        overlays = mock(AbstractOverlaySet.class);
        when(context.getWorld()).thenReturn(world);
        when(context.getGame()).thenReturn(game);
        when(world.getPlayer("test_bot")).thenReturn(Optional.of(player));
        when(world.getPlayer("offline")).thenReturn(Optional.empty());
        when(world.getCurrentTick()).thenReturn(123L);
        when(player.getUsername()).thenReturn("test_bot");
        when(player.getOverlays()).thenReturn(overlays);
        doAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return CompletableFuture.completedFuture(supplier.get());
        }).when(game).sync(any(Supplier.class));
        int port;
        try (var socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        server = LunaCompanionBridge.start(context, TOKEN, port);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
            ((ExecutorService) server.getExecutor()).shutdownNow();
        }
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void rejectsUnauthorizedRequestsAndUnknownRoutes() throws Exception {
        assertEquals(401, get("/trade?username=test_bot", null).statusCode());
        assertEquals(401, get("/trade?username=test_bot", "incorrect").statusCode());
        assertEquals(404, get("/missing", TOKEN).statusCode());
        assertEquals(400, get("/trade", TOKEN).statusCode());
        verify(context.getGame(), never()).sync(any(Supplier.class));
    }

    @Test
    void rejectsBrowserRequestsAndWrites() throws Exception {
        var uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/status");
        var browser = HttpRequest.newBuilder(uri).header("Origin", "https://example.com")
                .header("Authorization", "Bearer " + TOKEN).GET().build();
        assertEquals(403, client.send(browser, HttpResponse.BodyHandlers.ofString()).statusCode());
        var write = HttpRequest.newBuilder(uri).POST(HttpRequest.BodyPublishers.noBody()).build();
        assertEquals(405, client.send(write, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    @Test
    void reportsOfflinePlayerAndNoTrade() throws Exception {
        var offline = JsonParser.parseString(get("/trade?username=offline", TOKEN).body()).getAsJsonObject();
        assertTrue(offline.has("error"));
        var idle = JsonParser.parseString(get("/trade?username=test_bot", TOKEN).body()).getAsJsonObject();
        assertEquals("none", idle.get("stage").getAsString());
        assertEquals(123, idle.get("tick").getAsLong());
    }

    @Test
    void snapshotsBothOffersWithoutExposingPlayerInternals() throws Exception {
        OfferTradeInterface offer = setOffers();
        when(overlays.getOverlay(OfferTradeInterface.class)).thenReturn(offer);
        var response = get("/trade?username=test_bot", TOKEN);
        assertEquals(200, response.statusCode());
        var json = JsonParser.parseString(response.body()).getAsJsonObject();
        assertEquals("offer", json.get("stage").getAsString());
        assertEquals(100, json.getAsJsonArray("items").get(0).getAsJsonObject().get("amount").getAsInt());
        assertEquals(50, json.getAsJsonArray("otherItems").get(0).getAsJsonObject().get("amount").getAsInt());
        assertFalse(json.get("valuationAvailable").getAsBoolean());
        verify(player, never()).getPassword();
        verify(player, never()).getCurrentIp();
    }

    @Test
    void confirmationDoesNotExposeFirstScreenFlagsAsSecondScreenAcceptance() throws Exception {
        var offer = setOffers();
        var confirm = mock(ConfirmTradeInterface.class);
        when(confirm.getOffer()).thenReturn(offer);
        when(overlays.getOverlay(ConfirmTradeInterface.class)).thenReturn(confirm);
        var json = JsonParser.parseString(get("/trade?username=test_bot", TOKEN).body()).getAsJsonObject();
        assertEquals("confirm", json.get("stage").getAsString());
        assertFalse(json.has("accepted"));
        assertFalse(json.has("otherAccepted"));
        assertEquals(100, json.getAsJsonArray("items").get(0).getAsJsonObject().get("amount").getAsInt());
    }

    private OfferTradeInterface setOffers() {
        var offer = mock(OfferTradeInterface.class);
        var opposing = mock(OfferTradeInterface.class);
        var other = mock(Player.class);
        var otherOverlays = mock(AbstractOverlaySet.class);
        when(offer.getOther()).thenReturn(other);
        when(other.getUsername()).thenReturn("other_bot");
        when(other.getOverlays()).thenReturn(otherOverlays);
        when(otherOverlays.getOverlay(OfferTradeInterface.class)).thenReturn(opposing);
        var feathers = mock(ItemContainer.class);
        var coins = mock(ItemContainer.class);
        when(feathers.iterator()).thenAnswer(ignored -> List.of(new Item(314, 100)).iterator());
        when(coins.iterator()).thenAnswer(ignored -> List.of(new Item(995, 50)).iterator());
        when(offer.getItems()).thenReturn(feathers);
        when(opposing.getItems()).thenReturn(coins);
        return offer;
    }
}
