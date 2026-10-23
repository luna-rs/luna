package io.luna.game.model.collision;

import io.luna.game.model.Direction;
import io.luna.game.model.Entity;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.World;
import io.luna.game.model.chunk.Chunk;
import io.luna.game.model.chunk.ChunkManager;
import io.luna.game.model.chunk.ChunkRepository;
import io.luna.game.model.def.GameObjectDefinition;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.bot.Bot;
import io.luna.game.model.mob.interact.InteractionPolicy;
import io.luna.game.model.mob.interact.InteractionType;
import io.luna.game.model.object.GameObject;
import io.luna.game.model.object.ObjectDirection;
import io.luna.game.model.object.ObjectType;
import io.luna.game.model.path.route.RouteStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.google.common.collect.ImmutableList;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;

import static io.luna.game.model.collision.CollisionFlag.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CollisionManager}, run against in-memory matrices instead of a running world.
 *
 * @author hydrozoa
 */
final class CollisionManagerTest {

    /**
     * The collision matrices by chunk, standing in for the chunk repositories of a world.
     */
    private final Map<Chunk, CollisionMatrix[]> matrices = new HashMap<>();

    private CollisionManager manager;

    @BeforeEach
    void init() {
        matrices.clear();

        World world = Mockito.mock(World.class);
        ChunkManager chunks = Mockito.mock(ChunkManager.class);
        Mockito.when(world.getChunks()).thenReturn(chunks);
        Mockito.when(chunks.load(Mockito.any(Chunk.class))).thenAnswer(invocation -> {
            Chunk chunk = invocation.getArgument(0);
            ChunkRepository repository = Mockito.mock(ChunkRepository.class);
            Mockito.when(repository.getMatrices()).thenReturn(
                    matrices.computeIfAbsent(chunk, ignored -> CollisionMatrix.createMatrices(4, 8, 8)));
            return repository;
        });
        Mockito.when(chunks.load(Mockito.any(Position.class))).thenAnswer(invocation -> {
            Position position = invocation.getArgument(0);
            ChunkRepository repository = Mockito.mock(ChunkRepository.class);
            Mockito.when(repository.getChunk()).thenReturn(position.getChunk());
            Mockito.when(repository.getMatrices()).thenReturn(matricesAt(position));
            Mockito.when(repository.traversable(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyBoolean()))
                    .thenAnswer(step -> {
                        Position next = step.getArgument(0);
                        return !matrixAt(next).untraversable(next.getX() % Chunk.SIZE, next.getY() % Chunk.SIZE,
                                step.getArgument(1), step.getArgument(2));
                    });
            return repository;
        });
        manager = new CollisionManager(world);
    }

    private CollisionMatrix[] matricesAt(Position position) {
        return matrices.computeIfAbsent(position.getChunk(), chunk -> CollisionMatrix.createMatrices(4, 8, 8));
    }

    private CollisionMatrix matrixAt(Position position) {
        return matricesAt(position)[position.getZ()];
    }

    private int flagsAt(int x, int y, int z) {
        Position position = new Position(x, y, z);
        return matrixAt(position).get(x % Chunk.SIZE, y % Chunk.SIZE);
    }

    private static Entity mob(EntityType type, int size, Position[] position) {
        Entity entity = type == EntityType.NPC ? Mockito.mock(Npc.class) : Mockito.mock(Player.class);
        Mockito.when(entity.getType()).thenReturn(type);
        Mockito.when(entity.size()).thenReturn(size);
        Mockito.when(entity.sizeX()).thenReturn(size);
        Mockito.when(entity.sizeY()).thenReturn(size);
        Mockito.when(entity.getPosition()).thenAnswer(invocation -> position[0]);
        Mockito.when(entity.abs()).thenAnswer(invocation -> position[0]);
        return entity;
    }

    private Bot botAt(Position position) {
        Bot bot = Mockito.mock(Bot.class);
        Mockito.when(bot.abs()).thenReturn(position);
        Mockito.when(bot.isWithinDistance(Mockito.any(), Mockito.anyInt())).thenCallRealMethod();
        return bot;
    }

    @Test
    void botAndCombatAgreeOnDiagonalAndCardinalMeleeReach() {
        Npc target = (Npc) mob(EntityType.NPC, 1, new Position[]{new Position(111, 111, 0)});
        Position diagonal = new Position(110, 110, 0);
        assertFalse(manager.reached(botAt(diagonal), target, InteractionPolicy.STANDARD_SIZE));
        assertFalse(manager.reached(diagonal, target, InteractionPolicy.STANDARD_SIZE));
        Position beside = new Position(110, 111, 0);
        assertTrue(manager.reached(botAt(beside), target, InteractionPolicy.STANDARD_SIZE));
        assertTrue(manager.reached(beside, target, InteractionPolicy.STANDARD_SIZE));
    }

    @Test
    void adjacentBotCannotAttackThroughWall() {
        Position source = new Position(110, 111, 0);
        Npc target = (Npc) mob(EntityType.NPC, 1, new Position[]{new Position(111, 111, 0)});
        CollisionUpdate.Builder update = new CollisionUpdate.Builder();
        update.type(CollisionUpdateType.ADDING);
        update.flag(source, WALL_EAST);
        manager.apply(update.build(), false);
        assertFalse(manager.reached(botAt(source), target, InteractionPolicy.STANDARD_SIZE));
        assertFalse(manager.reached(source, target, InteractionPolicy.STANDARD_SIZE));
    }

    @Test
    void botRespectsExactTileAndRangedDistancePolicies() {
        Position source = new Position(110, 110, 0);
        assertFalse(manager.reached(botAt(source), new Position(111, 110, 0),
                new InteractionPolicy(InteractionType.SIZE, 0)));
        assertTrue(manager.reached(botAt(source), source, new InteractionPolicy(InteractionType.SIZE, 0)));
        assertTrue(manager.reached(botAt(source), new Position(120, 110, 0), InteractionPolicy.STANDARD_LINE_OF_SIGHT));
        assertFalse(manager.reached(botAt(source), new Position(121, 110, 0), InteractionPolicy.STANDARD_LINE_OF_SIGHT));
    }

    @Test
    void meleeReachUsesLargeNpcFootprint() {
        Npc target = (Npc) mob(EntityType.NPC, 2, new Position[]{new Position(111, 111, 0)});
        assertTrue(manager.reached(botAt(new Position(113, 112, 0)), target, InteractionPolicy.STANDARD_SIZE));
        assertFalse(manager.reached(botAt(new Position(113, 113, 0)), target, InteractionPolicy.STANDARD_SIZE));
    }

    private static void move(CollisionManager manager, Entity entity, Position[] position, Position to) {
        Position from = position[0];
        position[0] = to;
        manager.moveEntity(entity, from);
    }

    @Test
    void testNpcFlagsItsWholeFootprint() {
        Position[] position = {new Position(100, 100, 0)};
        manager.updateEntity(mob(EntityType.NPC, 2, position), false);

        for (int dx = 0; dx < 2; dx++) {
            for (int dy = 0; dy < 2; dy++) {
                assertEquals(BLOCK_NPCS, flagsAt(100 + dx, 100 + dy, 0));
            }
        }
        assertEquals(0, flagsAt(102, 100, 0));
        assertEquals(0, flagsAt(99, 100, 0));
    }

    @Test
    void testFlagsFollowAnNpcAndLeaveNothingBehind() {
        Position[] position = {new Position(100, 100, 0)};
        Entity npc = mob(EntityType.NPC, 2, position);
        manager.updateEntity(npc, false);

        move(manager, npc, position, new Position(101, 100, 0));
        assertEquals(0, flagsAt(100, 100, 0));
        assertEquals(0, flagsAt(100, 101, 0));
        assertEquals(BLOCK_NPCS, flagsAt(101, 100, 0));
        assertEquals(BLOCK_NPCS, flagsAt(102, 101, 0));

        manager.updateEntity(npc, true);
        for (int x = 99; x < 104; x++) {
            for (int y = 99; y < 103; y++) {
                assertEquals(0, flagsAt(x, y, 0));
            }
        }
    }

    @Test
    void testPlayersShareATile() {
        Position[] first = {new Position(110, 110, 0)};
        Position[] second = {new Position(110, 110, 0)};
        Entity a = mob(EntityType.PLAYER, 1, first);
        Entity b = mob(EntityType.PLAYER, 1, second);
        manager.updateEntity(a, false);
        manager.updateEntity(b, false);

        move(manager, a, first, new Position(111, 110, 0));
        assertEquals(BLOCK_PLAYERS, flagsAt(110, 110, 0));
        assertEquals(BLOCK_PLAYERS, flagsAt(111, 110, 0));

        manager.updateEntity(b, true);
        assertEquals(0, flagsAt(110, 110, 0));
    }

    @Test
    void testLargeNpcIsNotStoppedByItself() {
        Position[] position = {new Position(200, 200, 0)};
        manager.updateEntity(mob(EntityType.NPC, 2, position), false);

        for (Direction direction : Direction.ALL_EXCEPT_NONE) {
            assertTrue(manager.traversable(position[0], EntityType.NPC, direction, 2), direction.toString());
        }
        // Checking only the anchor tile, as a size 1 mob does, would run into its own footprint.
        assertFalse(manager.traversable(position[0], EntityType.NPC, Direction.EAST, 1));
    }

    @Test
    void testLargeNpcIsStoppedByOtherNpcsInItsPath() {
        Position[] ogre = {new Position(200, 200, 0)};
        manager.updateEntity(mob(EntityType.NPC, 2, ogre), false);
        manager.updateEntity(mob(EntityType.NPC, 1, new Position[]{new Position(202, 201, 0)}), false);

        assertFalse(manager.traversable(ogre[0], EntityType.NPC, Direction.EAST, 2));
        assertTrue(manager.traversable(ogre[0], EntityType.NPC, Direction.NORTH, 2));
        assertFalse(manager.traversable(ogre[0], EntityType.NPC, Direction.NORTH_EAST, 2));
        assertTrue(manager.traversable(ogre[0], EntityType.PLAYER, Direction.EAST, 2));
    }

    @Test
    void testLargeDiagonalNeedsTheCornerTile() {
        Position start = new Position(230, 230, 0);
        assertTrue(manager.traversable(start, EntityType.NPC, Direction.NORTH_EAST, 2));

        matrixAt(new Position(232, 232, 0)).flag(232 % Chunk.SIZE, 232 % Chunk.SIZE, LOC);
        assertFalse(manager.traversable(start, EntityType.NPC, Direction.NORTH_EAST, 2));
        assertTrue(manager.traversable(start, EntityType.NPC, Direction.EAST, 2));
        assertTrue(manager.traversable(start, EntityType.NPC, Direction.NORTH, 2));
    }

    @Test
    void testSizeOneAndSizeTwoAgreeOnOpenGround() {
        Position start = new Position(300, 300, 0);
        for (Direction direction : Direction.ALL_EXCEPT_NONE) {
            assertEquals(manager.traversable(start, EntityType.NPC, direction, 1),
                    manager.traversable(start, EntityType.NPC, direction, 2));
        }
    }

    @Test
    void testBlockedStrategyKeepsSizeOneNpcOnWater() {
        Position start = new Position(320, 320, 0);
        matrixAt(new Position(321, 320, 0)).flag(321 % Chunk.SIZE, 320 % Chunk.SIZE, BLOCK_WALK);

        assertTrue(manager.traversable(start, EntityType.NPC, Direction.EAST, 1, RouteStrategy.BLOCKED));
        assertFalse(manager.traversable(start, EntityType.NPC, Direction.WEST, 1, RouteStrategy.BLOCKED));
        assertFalse(manager.traversable(start, EntityType.NPC, Direction.EAST, 1, RouteStrategy.NORMAL));
        assertTrue(manager.traversable(start, EntityType.NPC, Direction.WEST, 1, RouteStrategy.NORMAL));
    }

    @Test
    void testBridgeShiftAppliesToMapDataOnly() {
        manager.markBridged(new Position(400, 400, 1));

        // A mob standing on the bridge keeps its collision on the plane it stands on.
        manager.updateEntity(mob(EntityType.NPC, 1, new Position[]{new Position(400, 400, 0)}), false);
        assertEquals(BLOCK_NPCS, flagsAt(400, 400, 0));

        // Map data is lowered, and map data on the ground under the bridge is dropped.
        CollisionUpdate.Builder map = new CollisionUpdate.Builder();
        map.type(CollisionUpdateType.ADDING);
        map.mapCoordinates();
        map.flag(new Position(400, 400, 1), LOC);
        map.flag(new Position(400, 400, 0), BLOCK_WALK);
        manager.apply(map.build(), true);
        assertEquals(BLOCK_NPCS | LOC, flagsAt(400, 400, 0));
        assertEquals(0, flagsAt(400, 400, 1));

        // An update that is not in map coordinates is left where it is.
        CollisionUpdate.Builder live = new CollisionUpdate.Builder();
        live.type(CollisionUpdateType.ADDING);
        live.flag(new Position(400, 400, 0), LOC_ROUTE_BLOCKER);
        manager.apply(live.build(), false);
        assertEquals(BLOCK_NPCS | LOC | LOC_ROUTE_BLOCKER, flagsAt(400, 400, 0));
    }

    @Test
    void testObjectIsLoweredByBridgeAsAWhole() {
        // A north facing wall on a bridged tile whose northern neighbour is not bridged, as at Lumbridge (3249, 3226).
        manager.markBridged(new Position(600, 600, 1));

        GameObjectDefinition definition = new GameObjectDefinition(1, "test", "test", 1, 1, 0, true, false, false,
                OptionalInt.empty(), ImmutableList.of(), false, null);
        GameObject wall = Mockito.mock(GameObject.class);
        Mockito.when(wall.def()).thenReturn(definition);
        Mockito.when(wall.getObjectType()).thenReturn(ObjectType.STRAIGHT_WALL);
        Mockito.when(wall.getDirection()).thenReturn(ObjectDirection.NORTH);
        Mockito.when(wall.getPosition()).thenReturn(new Position(600, 600, 1));

        CollisionUpdate.Builder map = new CollisionUpdate.Builder();
        map.type(CollisionUpdateType.ADDING);
        map.mapCoordinates();
        map.object(wall);
        manager.apply(map.build(), true);

        // Both halves of the wall land on the lowered level, and none are left behind on the level above.
        assertEquals(WALL_NORTH, flagsAt(600, 600, 0));
        assertEquals(WALL_SOUTH, flagsAt(600, 601, 0));
        assertEquals(0, flagsAt(600, 601, 1));
        assertFalse(manager.traversable(new Position(600, 600, 0), EntityType.PLAYER, Direction.NORTH, false));
        assertFalse(manager.traversable(new Position(600, 601, 0), EntityType.PLAYER, Direction.SOUTH, false));
    }

    @Test
    void testRemovingOneOfTwoOverlappingUpdatesKeepsTheFlag() {
        CollisionUpdate.Builder add = new CollisionUpdate.Builder();
        add.type(CollisionUpdateType.ADDING);
        add.flag(new Position(500, 500, 0), WALL_NORTH);
        CollisionUpdate adding = add.build();
        manager.apply(adding, false);
        manager.apply(adding, false);

        CollisionUpdate.Builder remove = new CollisionUpdate.Builder();
        remove.type(CollisionUpdateType.REMOVING);
        remove.flag(new Position(500, 500, 0), WALL_NORTH);
        CollisionUpdate removing = remove.build();

        manager.apply(removing, false);
        assertEquals(WALL_NORTH, flagsAt(500, 500, 0));
        manager.apply(removing, false);
        assertEquals(0, flagsAt(500, 500, 0));
    }
}
