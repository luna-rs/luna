package io.luna.game.model.path.route;

import io.luna.game.event.EventListener;
import io.luna.game.event.impl.GroundItemClickEvent.PickupItemEvent;
import io.luna.game.model.Direction;
import io.luna.game.model.Entity;
import io.luna.game.model.Position;
import io.luna.game.model.collision.CollisionUpdate;
import io.luna.game.model.collision.CollisionUpdateType;
import io.luna.game.model.item.GroundItem;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.interact.InteractionAction;
import io.luna.game.model.mob.interact.InteractionActionListener;
import io.luna.game.model.mob.interact.InteractionPolicy;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import static io.luna.game.model.collision.CollisionFlag.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Exercises ordinary pickup dispatch for items on tables without changing floor-item or wall reach rules.
 *
 * @author lare96
 */
final class GroundItemPickupReachTest {

    private static BiFunction<Player, Entity, InteractionPolicy> nativePickupPolicy;

    private final TestMap map = new TestMap();
    private final Position winePosition = new Position(2930, 3515);

    @BeforeAll
    static void nativePickupListener() throws Exception {
        api.bot.script.InventoryProductionFixtures.INSTANCE.initialize();
        List<EventListener<?>> listeners = api.predef.PropertiesPredefKt.getScriptListeners();
        int previousSize = listeners.size();
        try {
            Class.forName("engine.widget.PickupItem").getConstructor(String[].class)
                    .newInstance((Object) new String[0]);
            assertEquals(previousSize + 1, listeners.size());
            EventListener<?> listener = listeners.get(previousSize);
            assertEquals(PickupItemEvent.class, listener.getEventType());
            nativePickupPolicy = listener.getPolicy();
        } finally {
            listeners.subList(previousSize, listeners.size()).clear();
        }
    }

    @ParameterizedTest
    @EnumSource(value = Direction.class, names = {"NORTH", "EAST", "SOUTH", "WEST"})
    void wineOnALowTableCanBePickedUpFromEachAccessibleSide(Direction side) {
        map.flag(winePosition.getX(), winePosition.getY(), LOC);
        GroundItem wine = item(245, winePosition);
        Position stand = winePosition.translate(1, side);
        Player player = player(stand);

        assertFalse(StepValidator.canTravel(map.view(), 0, stand.getX(), stand.getY(),
                winePosition.getX() - stand.getX(), winePosition.getY() - stand.getY(), 1, 0,
                RouteStrategy.NORMAL));
        assertTrue(map.manager().raycast(stand, winePosition));
        assertFalse(map.manager().reached(player, wine, InteractionPolicy.EQUAL_POSITION));

        assertPickupDispatched(player, wine);
    }

    @Test
    void anOrdinaryFloorItemStillRequiresWalkingOntoItsTile() {
        GroundItem item = item(995, winePosition);
        Player player = player(winePosition.translate(-1, 0));

        assertPickupRejected(player, item);
    }

    @Test
    void aFloorItemUnderAnotherEntityDoesNotBecomeAnAdjacentPickup() {
        map.flag(winePosition.getX(), winePosition.getY(), BLOCK_NPCS | BLOCK_PLAYERS);
        GroundItem item = item(995, winePosition);
        Player player = player(winePosition.translate(-1, 0));

        assertPickupRejected(player, item);
    }

    @Test
    void standingOnAnOrdinaryFloorItemPicksItUpWithoutSteppingAway() {
        GroundItem item = item(995, winePosition);

        assertPickupDispatched(player(winePosition), item);
    }

    @Test
    void standingOnAnObjectTileStillPicksUpWithoutSteppingAway() {
        map.flag(winePosition.getX(), winePosition.getY(), LOC);

        assertPickupDispatched(player(winePosition), item(245, winePosition));
    }

    @Test
    void aWallBetweenThePlayerAndTableStillPreventsPickup() {
        map.flag(winePosition.getX(), winePosition.getY(), LOC);
        CollisionUpdate.Builder wall = new CollisionUpdate.Builder();
        wall.type(CollisionUpdateType.ADDING);
        wall.wall(winePosition, true, Direction.WEST);
        map.manager().apply(wall.build(), true);

        assertPickupRejected(player(winePosition.translate(-1, 0)), item(245, winePosition));
    }

    @ParameterizedTest
    @CsvSource({"-1, -1, 0", "2, 0, 0", "0, 0, 1", "-16, 0, 0"})
    void diagonalsDistanceAndDifferentPlanesDoNotReachTableWine(int dx, int dy, int plane) {
        map.flag(winePosition.getX(), winePosition.getY(), LOC);
        Position stand = new Position(winePosition.getX() + dx, winePosition.getY() + dy, plane);

        assertPickupRejected(player(stand), item(245, winePosition));
    }

    private void assertPickupDispatched(Player player, GroundItem item) {
        InteractionPolicy policy = nativePickupPolicy.apply(player, item);
        assertTrue(map.manager().reached(player, item, policy));
        AtomicInteger pickups = new AtomicInteger();
        InteractionAction action = action(player, item, policy, pickups);

        assertTrue(action.run());

        assertEquals(1, pickups.get());
        verify(player, never()).sendMessage(anyString());
        verify(player.getWalking()).clear();
        verifyNoInteractions(player.getNavigator());
    }

    private void assertPickupRejected(Player player, GroundItem item) {
        InteractionPolicy policy = nativePickupPolicy.apply(player, item);
        assertFalse(map.manager().reached(player, item, policy));
        AtomicInteger pickups = new AtomicInteger();
        InteractionAction action = action(player, item, policy, pickups);

        assertFalse(action.run());
        assertTrue(action.run());

        assertEquals(0, pickups.get());
        verify(player).sendMessage("I can't reach that!");
        verifyNoInteractions(player.getNavigator());
    }

    private InteractionAction action(Player player, GroundItem item, InteractionPolicy policy, AtomicInteger pickups) {
        var listeners = new ArrayList<>(List.of(new InteractionActionListener(policy, pickups::incrementAndGet)));
        return new InteractionAction(player, listeners, item, new PickupItemEvent(player, item));
    }

    private Player player(Position position) {
        Player player = mock(Player.class, RETURNS_DEEP_STUBS);
        when(player.getPosition()).thenReturn(position);
        when(player.abs()).thenReturn(position);
        when(player.getWorld().getCollisionManager()).thenReturn(map.manager());
        when(player.getWalking().isEmpty()).thenReturn(true);
        return player;
    }

    private GroundItem item(int id, Position position) {
        GroundItem item = mock(GroundItem.class);
        when(item.getId()).thenReturn(id);
        when(item.getPosition()).thenReturn(position);
        when(item.abs()).thenReturn(position);
        when(item.size()).thenReturn(1);
        when(item.sizeX()).thenReturn(1);
        when(item.sizeY()).thenReturn(1);
        return item;
    }
}
