package io.luna.game.model.path.route;

import io.luna.game.model.Position;
import io.luna.game.model.World;
import io.luna.game.model.chunk.Chunk;
import io.luna.game.model.chunk.ChunkManager;
import io.luna.game.model.chunk.ChunkRepository;
import io.luna.game.model.collision.CollisionManager;
import io.luna.game.model.collision.CollisionMatrix;
import io.luna.game.model.collision.CollisionUpdate;
import io.luna.game.model.collision.CollisionUpdateType;
import io.luna.game.model.collision.CollisionView;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

/**
 * A small open map held in memory, for testing code that reads collision data.
 * <p>
 * Every tile is open until flags are set on it. Tiles are on height level 0 unless stated.
 *
 * @author hydrozoa
 */
final class TestMap {

    /**
     * The collision matrices by chunk, standing in for the chunk repositories of a world.
     */
    private final Map<Chunk, CollisionMatrix[]> matrices = new HashMap<>();

    /**
     * The collision manager over the map.
     */
    private final CollisionManager manager;

    /**
     * Creates a new open map.
     */
    TestMap() {
        World world = Mockito.mock(World.class);
        ChunkManager chunks = Mockito.mock(ChunkManager.class);
        Mockito.when(world.getChunks()).thenReturn(chunks);
        Mockito.when(chunks.load(Mockito.any(Position.class))).thenAnswer(invocation -> {
            Position position = invocation.getArgument(0);
            return repository(position.getChunk());
        });
        Mockito.when(chunks.load(Mockito.any(Chunk.class))).thenAnswer(invocation -> repository(invocation.getArgument(0)));
        manager = new CollisionManager(world);
    }

    private ChunkRepository repository(Chunk chunk) {
        CollisionMatrix[] chunkMatrices = matrices.computeIfAbsent(chunk,
                key -> CollisionMatrix.createMatrices(4, 8, 8));
        ChunkRepository repository = Mockito.mock(ChunkRepository.class);
        Mockito.when(repository.getChunk()).thenReturn(chunk);
        Mockito.when(repository.getMatrices()).thenReturn(chunkMatrices);
        Mockito.when(repository.getSnapshot()).thenReturn(chunkMatrices);
        Mockito.when(repository.traversable(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyBoolean()))
                .thenAnswer(step -> {
                    Position next = step.getArgument(0);
                    return !matricesAt(next)[next.getZ()].untraversable(next.getX() % Chunk.SIZE,
                            next.getY() % Chunk.SIZE, step.getArgument(1), step.getArgument(2));
                });
        return repository;
    }

    private CollisionMatrix[] matricesAt(Position position) {
        return matrices.computeIfAbsent(position.getChunk(), chunk -> CollisionMatrix.createMatrices(4, 8, 8));
    }

    /**
     * Returns the collision manager over the map.
     */
    CollisionManager manager() {
        return manager;
    }

    /**
     * Returns a view of the map's collision flags.
     */
    CollisionView view() {
        return manager.view(true);
    }

    /**
     * Adds flags to a tile.
     *
     * @param x The x coordinate.
     * @param y The y coordinate.
     * @param mask The flags to add.
     */
    void flag(int x, int y, int mask) {
        flag(x, y, 0, mask);
    }

    /**
     * Adds flags to a tile.
     *
     * @param x The x coordinate.
     * @param y The y coordinate.
     * @param z The height level.
     * @param mask The flags to add.
     */
    void flag(int x, int y, int z, int mask) {
        CollisionUpdate.Builder builder = new CollisionUpdate.Builder();
        builder.type(CollisionUpdateType.ADDING);
        builder.flag(new Position(x, y, z), mask);
        manager.apply(builder.build(), true);
    }

    /**
     * Adds flags to every tile of a rectangle.
     *
     * @param x The x coordinate of the south west tile.
     * @param y The y coordinate of the south west tile.
     * @param width The width of the rectangle.
     * @param length The length of the rectangle.
     * @param mask The flags to add.
     */
    void fill(int x, int y, int width, int length, int mask) {
        for (int dx = 0; dx < width; dx++) {
            for (int dy = 0; dy < length; dy++) {
                flag(x + dx, y + dy, mask);
            }
        }
    }
}
