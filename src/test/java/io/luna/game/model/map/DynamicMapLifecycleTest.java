package io.luna.game.model.map;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.luna.LunaContext;
import io.luna.game.cache.map.*;
import io.luna.game.model.*;
import io.luna.game.model.chunk.*;
import io.luna.game.model.collision.*;
import io.luna.game.model.map.builder.*;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.controller.ControllerManager;
import io.luna.game.model.object.GameObjectList;
import io.luna.game.model.object.GameObject;
import io.luna.game.model.object.ObjectDirection;
import io.luna.game.model.object.ObjectType;
import io.luna.game.model.def.GameObjectDefinition;
import io.luna.game.model.mob.movement.WalkingQueue;
import java.util.OptionalInt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Runs map creation, membership and reclamation with real chunk repositories and collision in a small cache fixture.
 *
 * @author lare96
 */
class DynamicMapLifecycleTest {
    private LunaContext context;
    private World world;
    private ChunkManager chunks;
    private DynamicMapSpacePool pool;
    private MapTileGrid grid;
    private final Chunk source = new Chunk(400, 400);
    private final Position exit = new Position(3200, 3200);

    @BeforeEach
    void setup() {
        context = mock(LunaContext.class, RETURNS_DEEP_STUBS);
        world = mock(World.class, RETURNS_DEEP_STUBS);
        when(context.getWorld()).thenReturn(world);
        when(world.getContext()).thenReturn(context);
        Region region = source.getAbsPosition().getRegion();
        MapIndex index = new MapIndex(region, 1, 2, false);
        grid = mock(MapTileGrid.class);
        MapTile open = new MapTile(0, 0, 0, 0, 0, 0, 0, 0, 0);
        when(grid.getTile(any(Position.class))).thenReturn(open);
        MapIndexTable table = new MapIndexTable(ImmutableMap.of(region, index), new MapObjectSet(ImmutableList.of()),
                new MapTileGridSet(ImmutableMap.of(index, grid)));
        when(context.getCache().getMapIndexTable()).thenReturn(table);
        chunks = new ChunkManager(world);
        when(world.getChunks()).thenReturn(chunks);
        CollisionManager collision = new CollisionManager(world);
        when(world.getCollisionManager()).thenReturn(collision);
        when(world.getObjects()).thenReturn(new GameObjectList(world));
        pool = new DynamicMapSpacePool(context);
        when(world.getDynamicMapSpacePool()).thenReturn(pool);
    }

    private DynamicMap map(Function<Player, DynamicMapController> controller) {
        return new DynamicMap(context, source, new DynamicMapPalette().setChunk(6, 6, 0,
                new DynamicMapChunk(source, 0)), controller);
    }

    private Player player() {
        Player player = mock(Player.class, RETURNS_DEEP_STUBS);
        AtomicReference<DynamicMap> membership = new AtomicReference<>();
        AtomicReference<Position> position = new AtomicReference<>(exit);
        doAnswer(call -> { membership.set(call.getArgument(0)); return null; }).when(player).setDynamicMap(any());
        when(player.getDynamicMap()).thenAnswer(call -> membership.get());
        doAnswer(call -> { position.set(call.getArgument(0)); return null; }).when(player).move(any(Position.class));
        when(player.getPosition()).thenAnswer(call -> position.get());
        when(player.getControllers()).thenReturn(new ControllerManager(player));
        return player;
    }

    @Test
    void createsOpenCollisionAndReclaimsBlockedEmptySpace() {
        DynamicMap map = map(player -> new TestController(player, exit));
        map.create();
        Position position = map.getInstancePosition(source.getAbsPosition().translate(4, 4));
        ChunkRepository repository = chunks.load(position);
        assertTrue(repository.traversable(position, EntityType.PLAYER, Direction.NORTH, false));
        assertTrue(repository.traversable(position, EntityType.PLAYER, Direction.NORTH, true));
        assertFalse(repository.traversable(position.setZ(1), EntityType.PLAYER, Direction.NORTH, false));
        assertThrows(IllegalStateException.class, map::create);
        map.delete();
        assertNull(map.getAssignedSpace());
        assertTrue(pool.getInstances().isEmpty());
        assertFalse(repository.traversable(position, EntityType.PLAYER, Direction.NORTH, false));
        assertFalse(repository.traversable(position, EntityType.PLAYER, Direction.NORTH, true));
        assertDoesNotThrow(map::delete);
    }

    @Test
    void blockedTerrainRotatesAndOnlyUsesItsDestinationPlane() {
        MapTile blocked = mock(MapTile.class);
        when(blocked.isBlocked()).thenReturn(true);
        when(grid.getTile(source.getAbsPosition().translate(1, 2).setZ(3))).thenReturn(blocked);
        DynamicMap map = new DynamicMap(context, source, new DynamicMapPalette().setChunk(6, 6, 2,
                new DynamicMapChunk(source, 3, DynamicMapChunk.Rotation.CW_90_DEGREES)), player -> null);
        map.create();
        Position destination = map.getInstancePosition(source.getAbsPosition().translate(1, 2).setZ(3));
        assertEquals(map.getAssignedSpace().abs().translate(50, 54).setZ(2), destination);
        assertTrue(chunks.load(destination).getMatrices()[2].all(destination.getX() & 7, destination.getY() & 7,
                CollisionFlag.BLOCK_WALK));
        assertFalse(chunks.load(destination).getMatrices()[2].all((destination.getX() + 1) & 7, destination.getY() & 7,
                CollisionFlag.BLOCK_WALK));
        map.delete();
    }

    @Test
    void membershipRunsControllerHooksAndExitsWhenPositionLeavesPalette() {
        AtomicReference<TestController> controller = new AtomicReference<>();
        DynamicMap map = map(player -> {
            TestController next = new TestController(player, exit);
            controller.set(next);
            return next;
        });
        map.create();
        Player player = player();
        assertTrue(map.add(player));
        assertSame(map, player.getDynamicMap());
        assertEquals(1, controller.get().entered);
        assertFalse(map.add(player));
        player.move(map.getAssignedSpace().abs());
        player.getControllers().getPrimary().move();
        assertNull(player.getDynamicMap());
        assertEquals(exit, player.getPosition());
        assertEquals(1, controller.get().exited);
        assertFalse(map.remove(player));
        map.delete();
    }

    @Test
    void deletionEvacuatesMembersAndReplacingControllerExitsExactlyOnce() {
        TestController[] controller = new TestController[1];
        DynamicMap map = map(player -> controller[0] = new TestController(player, exit));
        map.create();
        Player player = player();
        map.add(player);
        player.getControllers().unregister();
        assertEquals(1, controller[0].exited);
        assertNull(player.getDynamicMap());
        map.add(player);
        map.delete();
        assertEquals(1, controller[0].exited);
        assertNull(player.getDynamicMap());
        assertEquals(exit, player.getPosition());
    }

    @Test
    void walkingIntoAnUnmappedTileExitsBeforeBlockedVoidCollision() {
        DynamicMap map = map(player -> new TestController(player, exit));
        map.create();
        Player player = player();
        Position edge = map.getInstancePosition(source.getAbsPosition().translate(7, 4));
        map.add(player, edge);
        when(player.getZ()).thenReturn(0);
        WalkingQueue walking = new WalkingQueue(player);
        walking.addStep(Direction.EAST);
        walking.process();
        assertNull(player.getDynamicMap());
        assertEquals(exit, player.getPosition());
        assertTrue(walking.isEmpty());
        map.delete();
    }

    @Test
    void nativeObjectsRepeatRotateAndCleanUpWithoutChangingTheSource() {
        int id = 14973;
        GameObjectDefinition definition = new GameObjectDefinition(id, "instance fixture", "fixture", 2, 3, 0,
                true, false, false, OptionalInt.empty(), ImmutableList.of(), false, null);
        GameObjectDefinition.ALL.storeDefinition(definition);
        Position real = source.getAbsPosition().translate(2, 3);
        MapObject object = new MapObject(id, real, ObjectType.DEFAULT, ObjectDirection.WEST);
        MapIndexTable previous = context.getCache().getMapIndexTable();
        MapIndexTable table = new MapIndexTable(previous.getIndexTable(), new MapObjectSet(ImmutableList.of(object)),
                previous.getTileSet());
        when(context.getCache().getMapIndexTable()).thenReturn(table);
        DynamicMap map = new DynamicMap(context, source, new DynamicMapPalette()
                .setChunk(6, 6, 0, new DynamicMapChunk(source, 0, DynamicMapChunk.Rotation.CW_90_DEGREES))
                .setChunk(8, 8, 2, new DynamicMapChunk(source, 0)), player -> null);
        map.create();
        assertEquals(2, world.getObjects().size());
        Position rotated = map.getAssignedSpace().abs().translate(51, 52);
        GameObject copy = world.getObjects().iterator().next();
        for (GameObject candidate : world.getObjects()) if (candidate.getPosition().equals(rotated)) copy = candidate;
        assertEquals(rotated, copy.getPosition());
        assertEquals(ObjectDirection.NORTH, copy.getDirection());
        assertFalse(copy.isDynamic());
        assertTrue(chunks.load(rotated).getMatrices()[0].all(rotated.getX() & 7, rotated.getY() & 7, CollisionFlag.LOC));
        assertTrue(world.getObjects().findAll(real).findAny().isEmpty());
        map.delete();
        assertEquals(0, world.getObjects().size());
        assertTrue(chunks.load(rotated).getAll().values().stream().allMatch(java.util.Set::isEmpty));
    }

    @Test
    void failedCreationReturnsItsAllocationAndNeverAdmitsPlayers() {
        Chunk missing = source.translate(40, 40);
        DynamicMap map = new DynamicMap(context, missing, new DynamicMapPalette()
                .setChunk(6, 6, 0, new DynamicMapChunk(missing, 0)), player -> null);
        assertThrows(IllegalStateException.class, map::create);
        assertNull(map.getAssignedSpace());
        assertTrue(pool.getInstances().isEmpty());
        assertThrows(IllegalStateException.class, () -> map.add(player()));
    }

    @Test
    void instanceControllerMustBelongToTheEnteringPlayer() {
        Player owner = player();
        DynamicMap map = map(ignored -> new TestController(owner, exit));
        map.create();
        Player other = player();
        assertThrows(IllegalArgumentException.class, () -> map.add(other));
        assertNull(other.getDynamicMap());
        map.delete();
    }

    @Test
    void savingUsesSeparateRecoveryWithoutMovingOrExiting() {
        TestController[] controller = new TestController[1];
        DynamicMap map = map(player -> controller[0] = new TestController(player, exit));
        map.create();
        Player player = player();
        when(player.getHashedPassword()).thenReturn("already-hashed");
        when(player.getFriends()).thenReturn(java.util.Set.of());
        when(player.getIgnores()).thenReturn(java.util.Set.of());
        map.add(player);
        Position inside = player.getPosition();
        io.luna.game.persistence.PlayerData save = new io.luna.game.persistence.PlayerData("fixture").save(player);
        assertEquals(exit.translate(1, 1), save.position);
        assertEquals(inside, player.getPosition());
        assertSame(map, player.getDynamicMap());
        assertEquals(0, controller[0].exited);
        map.delete();
        assertEquals(exit, player.getPosition());
        assertEquals(1, controller[0].exited);
    }

    /**
     * Counts membership callbacks without starting any gameplay activity.
     *
     * @author lare96
     */
    private static final class TestController extends DynamicMapController {
        private final Position destination;
        private int entered;
        private int exited;

        private TestController(Player player, Position destination) {
            super(player);
            this.destination = destination;
        }

        @Override
        public void enter(Player player) {
            entered++;
        }

        @Override
        public Position exit(Player player) {
            exited++;
            return destination;
        }

        @Override
        public Position recovery(Player player) {
            return destination.translate(1, 1);
        }
    }
}
