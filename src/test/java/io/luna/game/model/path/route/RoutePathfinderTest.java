package io.luna.game.model.path.route;

import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.World;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.movement.PathfinderType;
import io.luna.game.model.path.PathResult;
import io.luna.game.model.path.PathResultType;
import io.luna.game.model.path.Pathfinders;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static io.luna.game.model.collision.CollisionFlag.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RoutePathfinder}.
 *
 * @author hydrozoa
 */
final class RoutePathfinderTest {

    private final TestMap map = new TestMap();

    private RoutePathfinder pathfinder(int size, int extraFlag) {
        return new RoutePathfinder(map.manager(), size, extraFlag, RouteStrategy.NORMAL);
    }

    private static List<Position> list(PathResult<Position> result) {
        return new ArrayList<>(result.getPath());
    }

    @Test
    void testCompletePath() {
        PathResult<Position> result = pathfinder(1, 0).find(new Position(100, 100), new Position(108, 100));
        assertEquals(PathResultType.COMPLETE, result.getType());
        assertEquals(List.of(new Position(108, 100)), list(result));
    }

    @Test
    void testAlreadyThereNeedsNoMovement() {
        PathResult<Position> result = pathfinder(1, 0).find(new Position(100, 100), new Position(100, 100));
        assertEquals(PathResultType.EMPTY, result.getType());
        assertTrue(result.getPath().isEmpty());
    }

    @Test
    void testUnreachableDestinationGivesAPartialPath() {
        map.fill(108, 108, 5, 1, LOC);
        map.fill(108, 112, 5, 1, LOC);
        map.fill(108, 109, 1, 3, LOC);
        map.fill(112, 109, 1, 3, LOC);

        PathResult<Position> result = pathfinder(1, 0).find(new Position(100, 100), new Position(110, 110));
        assertEquals(PathResultType.PARTIAL, result.getType());
        assertFalse(result.getPath().isEmpty());
    }

    @Test
    void testDifferentHeightLevelsFail() {
        PathResult<Position> result = pathfinder(1, 0).find(new Position(100, 100, 0), new Position(100, 100, 1));
        assertEquals(PathResultType.FAILED, result.getType());
    }

    @Test
    void testBoxedInMobCannotMove() {
        for (int x = 99; x <= 101; x++) {
            for (int y = 99; y <= 101; y++) {
                if (x != 100 || y != 100) {
                    map.flag(x, y, LOC);
                }
            }
        }
        PathResult<Position> result = pathfinder(1, 0).find(new Position(100, 100), new Position(110, 100));
        assertEquals(PathResultType.FAILED, result.getType());
    }

    @Test
    void testNpcsRouteAroundOtherMobs() {
        map.flag(104, 100, BLOCK_NPCS);
        map.flag(104, 101, BLOCK_PLAYERS);

        PathResult<Position> player = pathfinder(1, 0).find(new Position(100, 100), new Position(108, 100));
        assertEquals(1, player.getPath().size());

        PathResult<Position> npc = pathfinder(1, BLOCK_NPCS | BLOCK_PLAYERS)
                .find(new Position(100, 100), new Position(108, 100));
        assertEquals(PathResultType.COMPLETE, npc.getType());
        assertTrue(npc.getPath().size() > 1);
    }

    @Test
    void testNonNormalStrategyDoesNotFallBackToTheOldPathfinder() {
        RoutePathfinder water = new RoutePathfinder(map.manager(), 1, 0, RouteStrategy.BLOCKED);
        PathResult<Position> result = water.find(new Position(100, 100), new Position(300, 100));
        assertEquals(PathResultType.FAILED, result.getType());
    }

    private Mob mob(Class<? extends Mob> type, EntityType entityType, int size) {
        Mob mob = Mockito.mock(type);
        World world = Mockito.mock(World.class);
        Mockito.when(world.getCollisionManager()).thenReturn(map.manager());
        Mockito.when(mob.getWorld()).thenReturn(world);
        Mockito.when(mob.getType()).thenReturn(entityType);
        Mockito.when(mob.size()).thenReturn(size);
        Mockito.when(mob.getRouteStrategy()).thenReturn(RouteStrategy.NORMAL);
        return mob;
    }

    @Test
    void testPlayersWalkThroughNpcsAndOtherPlayers() {
        map.flag(104, 100, BLOCK_NPCS);
        map.flag(106, 100, BLOCK_PLAYERS);
        Position start = new Position(100, 100);
        Position end = new Position(110, 100);

        Mob player = mob(Player.class, EntityType.PLAYER, 1);
        assertEquals(1, PathfinderType.PLAYER.getPfFunction().apply(player).find(start, end).getPath().size());
        assertEquals(1, Pathfinders.forPlayer(player).find(start, end).getPath().size());
    }

    @Test
    void testNpcsGoAroundNpcsAndPlayers() {
        map.flag(104, 100, BLOCK_NPCS);
        Position start = new Position(100, 100);
        Position end = new Position(108, 100);

        Mob npc = mob(Npc.class, EntityType.NPC, 1);
        assertTrue(PathfinderType.NPC.getPfFunction().apply(npc).find(start, end).getPath().size() > 1);
        assertTrue(Pathfinders.forNpc(npc).find(start, end).getPath().size() > 1);

        // The same goes for a player standing in the way.
        TestMap other = new TestMap();
        other.flag(104, 100, BLOCK_PLAYERS);
        World world = Mockito.mock(World.class);
        Mockito.when(world.getCollisionManager()).thenReturn(other.manager());
        Mob walker = Mockito.mock(Npc.class);
        Mockito.when(walker.getWorld()).thenReturn(world);
        Mockito.when(walker.size()).thenReturn(1);
        Mockito.when(walker.getRouteStrategy()).thenReturn(RouteStrategy.NORMAL);
        assertTrue(Pathfinders.forNpc(walker).find(start, end).getPath().size() > 1);
    }

    @Test
    void testPathfindersAreSizedForTheirMob() {
        // A wall taller than the search area, with a gap that is one tile wide.
        map.fill(105, 30, 1, 70, LOC);
        map.fill(105, 101, 1, 70, LOC);
        Position start = new Position(100, 100);
        Position end = new Position(110, 100);

        Mob small = mob(Player.class, EntityType.PLAYER, 1);
        assertEquals(PathResultType.COMPLETE, Pathfinders.forPlayer(small).find(start, end).getType());

        Mob large = mob(Npc.class, EntityType.NPC, 2);
        assertEquals(PathResultType.PARTIAL, Pathfinders.forNpc(large).find(start, end).getType());
    }

    @Test
    void testFarDestinationsUseTheLongRangePathfinder() {
        Mob player = mob(Player.class, EntityType.PLAYER, 1);
        PathResult<Position> result = Pathfinders.forPlayer(player).find(new Position(100, 100), new Position(200, 100));
        assertEquals(PathResultType.COMPLETE, result.getType());
        assertEquals(new Position(200, 100), result.getPath().peekLast());
    }
}
