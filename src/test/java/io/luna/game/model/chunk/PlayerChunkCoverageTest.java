package io.luna.game.model.chunk;

import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.World;
import io.luna.game.model.WorldLocator;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies that player chunk coverage never skips a potentially nonempty aggression search.
 *
 * @author lare96
 */
class PlayerChunkCoverageTest {

    private World world;
    private ChunkManager manager;

    @BeforeEach
    void setUp() {
        world = mock(World.class, RETURNS_DEEP_STUBS);
        manager = new ChunkManager(world);
        when(world.getChunks()).thenReturn(manager);
    }

    @Test
    void emptyAndRemoteQueriesDoNotLoadRepositories() {
        assertFalse(manager.hasPlayersNearby(new Chunk(394, 394)));
        assertTrue(manager.getAll().isEmpty());

        addPlayer(new Position(3200, 3200));
        assertFalse(manager.hasPlayersNearby(new Chunk(1000, 1000)));
        assertEquals(1, manager.getAll().size());
    }

    @Test
    void coversEverySearchChunkAndStopsBeyondItsRadius() {
        Player player = addPlayer(new Position(3200, 3200));
        Chunk source = player.getPosition().getChunk();
        int radius = Math.floorDiv(Position.VIEWING_DISTANCE, Chunk.SIZE) + 1;

        for (int x = -radius - 1; x <= radius + 1; x++) {
            for (int y = -radius - 1; y <= radius + 1; y++) {
                assertEquals(Math.abs(x) <= radius && Math.abs(y) <= radius,
                        manager.hasPlayersNearby(source.translate(x, y)), "offset " + x + ", " + y);
            }
        }
        assertEquals(1, manager.getAll().size());
    }

    @Test
    void sharedChunkRemainsCoveredUntilItsLastPlayerLeaves() {
        Player first = addPlayer(new Position(3200, 3200));
        Player second = addPlayer(new Position(3207, 3207, 3));
        ChunkRepository repository = manager.load(first.getPosition());
        Player absent = mock(Player.class);
        when(absent.getType()).thenReturn(EntityType.PLAYER);

        repository.add(first);
        repository.remove(absent);
        repository.remove(first);
        repository.remove(first);
        assertTrue(manager.hasPlayersNearby(repository.getChunk()));

        repository.remove(second);
        assertFalse(manager.hasPlayersNearby(repository.getChunk()));
        repository.remove(second);
        repository.add(first);
        assertTrue(manager.hasPlayersNearby(repository.getChunk()));
        repository.remove(first);
        assertFalse(manager.hasPlayersNearby(repository.getChunk()));
    }

    @Test
    void overlappingCoverageSurvivesRemovalOfOneOccupiedChunk() {
        Player first = addPlayer(new Position(3200, 3200));
        Player second = addPlayer(new Position(3216, 3200));
        Chunk firstChunk = first.getPosition().getChunk();
        Chunk secondChunk = second.getPosition().getChunk();

        manager.load(firstChunk).remove(first);
        assertFalse(manager.hasPlayersNearby(firstChunk.translate(-2, 0)));
        assertTrue(manager.hasPlayersNearby(firstChunk));
        assertTrue(manager.hasPlayersNearby(secondChunk));

        manager.load(secondChunk).remove(second);
        assertFalse(manager.hasPlayersNearby(firstChunk));
        assertFalse(manager.hasPlayersNearby(secondChunk));
    }

    @Test
    void chunkMovesAndTeleportsUpdateCoverageImmediately() {
        Player player = addPlayer(new Position(3200, 3200));
        Chunk original = player.getPosition().getChunk();
        movePlayer(player, new Position(3208, 3200));
        assertFalse(manager.hasPlayersNearby(original.translate(-2, 0)));
        assertTrue(manager.hasPlayersNearby(original.translate(3, 0)));

        Position destination = new Position(10000, 10000, 2);
        movePlayer(player, destination);
        assertFalse(manager.hasPlayersNearby(original));
        assertTrue(manager.hasPlayersNearby(destination.getChunk()));

        manager.load(destination).remove(player);
        assertFalse(manager.hasPlayersNearby(destination.getChunk()));
    }

    @Test
    void nonPlayersDoNotContributeCoverage() {
        Npc npc = mock(Npc.class);
        when(npc.getType()).thenReturn(EntityType.NPC);
        ChunkRepository repository = manager.load(new Position(3200, 3200));
        repository.add(npc);
        assertFalse(manager.hasPlayersNearby(repository.getChunk()));
        repository.remove(npc);
        assertFalse(manager.hasPlayersNearby(repository.getChunk()));
    }

    @Test
    void coverageBelongsToItsWorld() {
        ChunkManager other = new ChunkManager(mock(World.class, RETURNS_DEEP_STUBS));
        Player player = addPlayer(new Position(3200, 3200));
        Chunk chunk = player.getPosition().getChunk();
        assertTrue(manager.hasPlayersNearby(chunk));
        assertFalse(other.hasPlayersNearby(chunk));
    }

    @Test
    void negativeAndLargeCoordinatesDoNotAliasOtherChunks() {
        List<Position> positions = List.of(new Position(-3200, -6400),
                new Position(1000000, 2000000), new Position(Integer.MIN_VALUE + 32, Integer.MAX_VALUE - 32));
        for (Position position : positions) {
            Player player = addPlayer(position);
            Chunk chunk = position.getChunk();
            assertTrue(manager.hasPlayersNearby(chunk));
            assertTrue(manager.hasPlayersNearby(chunk.translate(-2, 2)));
            assertFalse(manager.hasPlayersNearby(chunk.translate(3, 0)));
            manager.load(position).remove(player);
            assertFalse(manager.hasPlayersNearby(chunk));
        }
    }

    @Test
    void prefilterNeverRejectsAnExistingLocatorResultAcrossChunkEdgesAndPlanes() {
        Random random = new Random(666);
        List<Player> players = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            players.add(addPlayer(new Position(3180 + random.nextInt(48), 3180 + random.nextInt(48), i % 4)));
        }
        WorldLocator locator = new WorldLocator(world);
        for (int i = 0; i < 500; i++) {
            Position position = new Position(3150 + random.nextInt(108), 3150 + random.nextInt(108), i % 4);
            boolean expected = players.stream().anyMatch(player ->
                    player.getPosition().isWithinDistance(position, Position.VIEWING_DISTANCE));
            assertEquals(expected, !locator.findViewablePlayers(position).isEmpty());
            if (expected) {
                assertTrue(manager.hasPlayersNearby(position.getChunk()), "position " + position);
            }
        }
    }

    private Player addPlayer(Position position) {
        Player player = mock(Player.class);
        when(player.getType()).thenReturn(EntityType.PLAYER);
        when(player.getPosition()).thenReturn(position);
        manager.load(position).add(player);
        return player;
    }

    private void movePlayer(Player player, Position position) {
        manager.load(player.getPosition()).remove(player);
        when(player.getPosition()).thenReturn(position);
        manager.load(position).add(player);
    }
}
