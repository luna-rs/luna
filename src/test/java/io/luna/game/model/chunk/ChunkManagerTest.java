package io.luna.game.model.chunk;

import io.luna.game.model.Position;
import io.luna.game.model.World;
import io.luna.game.model.mob.Player;
import io.luna.net.msg.GameMessageWriter;
import io.luna.net.msg.out.ClearChunkMessageWriter;
import io.luna.net.msg.out.GroupedEntityMessageWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChunkManagerTest {

    /**
     * The region base the client was sent. The loaded map starts (tile 0) at 3152 and ends at 3255 on both axes,
     * because the base chunk sits six chunks in from the bottom-left of the map.
     */
    private static final Position REGION = new Position(3200, 3200);
    private static final int ORIGIN = 3152;

    /**
     * The highest local tile a chunk can start on, so that all eight of its tiles are inside the loaded map.
     */
    private static final int MAX_LOCAL_CHUNK_TILE = 96;

    private ChunkManager manager;

    @BeforeEach
    void setUp() {
        manager = new ChunkManager(mock(World.class, RETURNS_DEEP_STUBS));
    }

    /**
     * A tile at {@code x}, {@code y} in the client's loaded map. A player is only moved to a new region base once they
     * are within 16 tiles of the map's edge, so they can stand anywhere from 16 to 87 without a region change.
     */
    private static Position local(int x, int y) {
        return new Position(ORIGIN + x, ORIGIN + y);
    }

    private static int[] localBounds(List<ChunkRepository> chunks) {
        IntSummaryStatistics xs = chunks.stream()
                .mapToInt(chunk -> chunk.getChunk().getAbsPosition().getLocalX(REGION)).summaryStatistics();
        IntSummaryStatistics ys = chunks.stream()
                .mapToInt(chunk -> chunk.getChunk().getAbsPosition().getLocalY(REGION)).summaryStatistics();
        return new int[]{xs.getMin(), xs.getMax(), ys.getMin(), ys.getMax()};
    }

    private Player player(Position position, Position region) {
        Player player = mock(Player.class);
        when(player.getPosition()).thenReturn(position);
        when(player.getLastRegion()).thenReturn(region);
        return player;
    }

    private void queueUpdateAt(Position tile) {
        ChunkUpdatableRequest request =
                new ChunkUpdatableRequest(ChunkUpdatableView::globalView, new ChunkUpdatableMessage() {}, false);
        manager.load(tile).queueUpdate(request);
    }

    private List<GameMessageWriter> queuedMessages(Player player, int expected) {
        ArgumentCaptor<GameMessageWriter> captor = ArgumentCaptor.forClass(GameMessageWriter.class);
        verify(player, times(expected)).queue(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void viewableChunksCoverTheFullRadiusWhenCenteredInRegion() {
        List<ChunkRepository> chunks = manager.findViewableChunks(local(52, 52), REGION);
        assertEquals(49, chunks.size());
        assertArrayEquals(new int[]{24, 72, 24, 72}, localBounds(chunks));
    }

    @Test
    void viewableChunksAreClampedAtTheLowEdgeOfTheRegion() {
        // Sixteen tiles in is as close as a player can get before the region is re-based, and the radius reaches -8.
        List<ChunkRepository> chunks = manager.findViewableChunks(local(16, 16), REGION);
        assertEquals(36, chunks.size());
        assertArrayEquals(new int[]{0, 40, 0, 40}, localBounds(chunks));
    }

    @Test
    void viewableChunksAreClampedAtTheHighEdgeOfTheRegion() {
        // The radius reaches 104 here, one chunk past the last one the client can index.
        List<ChunkRepository> chunks = manager.findViewableChunks(local(87, 87), REGION);
        assertEquals(36, chunks.size());
        assertArrayEquals(new int[]{56, 96, 56, 96}, localBounds(chunks));
    }

    @Test
    void viewableChunksAreClampedIndependentlyOnEachAxis() {
        List<ChunkRepository> chunks = manager.findViewableChunks(local(16, 87), REGION);
        assertEquals(36, chunks.size());
        assertArrayEquals(new int[]{0, 40, 56, 96}, localBounds(chunks));
    }

    @Test
    void viewableChunksMatchTheUnclampedAreaLimitedToTilesTheClientCanIndex() {
        for (int x = 16; x <= 87; x++) {
            for (int y = 16; y <= 87; y++) {
                Position base = local(x, y);
                List<ChunkRepository> expected = manager.findViewableChunks(base).stream().filter(chunk -> {
                    Position placement = chunk.getChunk().getAbsPosition();
                    int localX = placement.getLocalX(REGION);
                    int localY = placement.getLocalY(REGION);
                    return localX >= 0 && localX <= MAX_LOCAL_CHUNK_TILE && localY >= 0 && localY <= MAX_LOCAL_CHUNK_TILE;
                }).collect(Collectors.toList());
                assertEquals(expected, manager.findViewableChunks(base, REGION), "base " + base);
            }
        }
    }

    @Test
    void unclampedViewableChunksStillCoverTheFullRadiusAtTheEdgeOfTheRegion() {
        List<ChunkRepository> chunks = manager.findViewableChunks(local(16, 16));
        assertEquals(49, chunks.size());
        assertArrayEquals(new int[]{-8, 40, -8, 40}, localBounds(chunks));
    }

    @Test
    void noChunksAreLoadedForABaseOutsideTheRegion() {
        assertTrue(manager.findViewableChunks(local(300, 300), REGION).isEmpty());
        assertTrue(manager.findViewableChunks(local(-300, 52), REGION).isEmpty());
        assertEquals(0, manager.getAll().size());
    }

    @Test
    void sendUpdatesSkipsChunksOutsideTheClientRegion() {
        // Moving from chunk 3 to chunk 2 puts chunk -1 in view, which the client has no map data for.
        Position oldPosition = local(28, 52);
        Player player = player(local(20, 52), REGION);
        queueUpdateAt(local(-4, 52));
        queueUpdateAt(local(4, 52));

        manager.sendUpdates(player, oldPosition, false);

        // Only the update for chunk 0, which stays in view, is sent.
        List<GameMessageWriter> queued = queuedMessages(player, 1);
        assertInstanceOf(GroupedEntityMessageWriter.class, queued.get(0));
    }

    @Test
    void sendUpdatesSkipsChunksOutsideTheClientRegionOnAFullRefresh() {
        Player player = player(local(16, 16), REGION);
        queueUpdateAt(local(-4, -4));
        queueUpdateAt(local(4, 4));

        manager.sendUpdates(player, local(16, 16), true);

        List<GameMessageWriter> queued = queuedMessages(player, 2);
        assertInstanceOf(ClearChunkMessageWriter.class, queued.get(0));
        assertInstanceOf(GroupedEntityMessageWriter.class, queued.get(1));
    }

    @Test
    void sendUpdatesStillSendsNewChunksInsideTheClientRegion() {
        // Moving from chunk 5 to chunk 6 puts chunk 9 in view, and it is well inside the loaded map.
        Player player = player(local(52, 52), REGION);
        queueUpdateAt(local(76, 52));

        manager.sendUpdates(player, local(44, 52), false);

        List<GameMessageWriter> queued = queuedMessages(player, 2);
        assertInstanceOf(ClearChunkMessageWriter.class, queued.get(0));
        assertInstanceOf(GroupedEntityMessageWriter.class, queued.get(1));
    }

    @Test
    void sendUpdatesSendsEveryViewableChunkAfterARegionChange() {
        // After a region change the player is at the center of a new map, so nothing in view is skipped.
        Position center = local(16, 16);
        Player player = player(center, center);
        queueUpdateAt(new Position(center.getX() - 24, center.getY() - 24));
        queueUpdateAt(new Position(center.getX() + 24, center.getY() + 24));

        manager.sendUpdates(player, center, true);

        verify(player, times(4)).queue(any(GameMessageWriter.class));
    }
}
