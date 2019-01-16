package io.luna.game.model.chunk;

import io.luna.LunaConstants;
import io.luna.game.model.Entity;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.StationaryEntity;
import io.luna.game.model.item.GroundItem;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.object.GameObject;
import io.luna.net.msg.out.ClearChunkMessageWriter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A model that loads new chunks and manages chunks that have already been loaded.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class ChunkManager implements Iterable<Chunk> {

    /**
     * A value that determines how many layers of chunks will be loaded around the Player, when looking
     * for viewable mobs.
     */
    private static final int RADIUS = 2;

    /**
     * A map of loaded chunks.
     */
    private final Map<ChunkPosition, Chunk> chunks = new HashMap<>(128); // TODO Proper initial size after cache loading.

    @Override
    public Spliterator<Chunk> spliterator() {
        return Spliterators.spliterator(chunks.values(), Spliterator.NONNULL);
    }

    @Override
    public Iterator<Chunk> iterator() {
        return chunks.values().iterator();
    }

    /**
     * Retrieves a chunk based on the argued chunk position, constructing and loading a new one if needed.
     *
     * @param position The position to construct a new chunk with.
     * @return The existing or newly loaded chunk.
     */
    public Chunk getChunk(ChunkPosition position) {
        return chunks.computeIfAbsent(position, Chunk::new);
    }

    /**
     * A shortcut to {@link #updateSet(Player, EntityType)} for type {@code PLAYER}.
     */
    public Set<Player> playerUpdateSet(Player player) {
        return updateSet(player, EntityType.PLAYER);
    }

    /**
     * A shortcut to {@link #updateSet(Player, EntityType)} for type {@code NPC}.
     */
    public Set<Npc> npcUpdateSet(Player player) {
        return updateSet(player, EntityType.NPC);
    }

    /**
     * Returns an update set for {@code type}, potentially sorted by the {@link ChunkMobComparator}.
     *
     * @param player The player.
     * @param type The entity type.
     * @param <T> The type.
     * @return The update set.
     */
    private <T extends Mob> Set<T> updateSet(Player player, EntityType type) {
        Set<T> updateSet = LunaConstants.STAGGERED_UPDATING ?
                new TreeSet<>(new ChunkMobComparator(player)) : new HashSet();
        ChunkPosition position = player.getChunkPosition();
        for (int x = -RADIUS; x < RADIUS; x++) {
            for (int y = -RADIUS; y < RADIUS; y++) {
                // Synchronize over the chunks so that the updating threads cannot modify them at the
                // same time.
                synchronized (chunks) {
                    Set<T> players = getChunk(position.translate(x, y)).getAll(type);
                    for (T inside : players) {
                        if (inside.isViewableFrom(player)) {
                            updateSet.add(inside);
                        }
                    }
                }
            }
        }
        return updateSet;
    }

    /**
     * Updates entities within this chunk.
     *
     * @param player The player.
     */
    public void updateEntities(Player player) {
        ChunkPosition position = player.getChunkPosition();
        for (int x = -RADIUS; x < RADIUS; x++) {
            for (int y = -RADIUS; y < RADIUS; y++) {
                Chunk chunk = getChunk(position.translate(x, y));

                // Clear chunk.
                Position chunkPos = chunk.getAbsolutePosition();
                player.queue(new ClearChunkMessageWriter(chunkPos));

                // Repopulate chunk entities.
                Set<GameObject> objectSet = chunk.getAll(EntityType.OBJECT);
                for (GameObject object : objectSet) {
                    // TODO Do not update cache loaded objects!
                    updateEntity(player, object);
                }

                Set<GroundItem> itemSet = chunk.getAll(EntityType.ITEM);
                for (GroundItem item : itemSet) {
                    updateEntity(player, item);
                }
            }
        }
    }

    /**
     * Updates a single entity.
     *
     * @param player The player.
     * @param entity The entity.
     */
    private void updateEntity(Player player, StationaryEntity entity) {
        Optional<Player> updatePlr = entity.getPlayer();
        boolean isUpdate = !updatePlr.isPresent() || updatePlr.map(player::equals).orElse(false);
        if (isUpdate) {
            entity.show();
        }
    }

    /**
     * Returns a set of viewable entities.
     *
     * @param position The relative position.
     * @param type The entity type.
     * @param <T> The type.
     * @return The set.
     */
    public <T extends Entity> Set<T> getViewableEntities(Position position, EntityType type) {
        Set<T> viewable = new HashSet<>();
        ChunkPosition chunkPos = position.getChunkPosition();
        for (int x = -RADIUS; x < RADIUS; x++) {
            for (int y = -RADIUS; y < RADIUS; y++) {
                Chunk chunk = getChunk(chunkPos.translate(x, y));
                Set<T> entities = chunk.getAll(type);
                for (T inside : entities) {
                    if (inside.getPosition().isViewable(position)) {
                        viewable.add(inside);
                    }
                }
            }
        }
        return viewable;
    }


    /**
     * Returns a list of viewable chunks.
     *
     * @param position The relative position.
     * @return The list.
     */
    public List<Chunk> getViewableChunks(Position position) {
        List<Chunk> viewable = new ArrayList<>(16);
        ChunkPosition chunkPos = position.getChunkPosition();
        for (int x = -RADIUS; x < RADIUS; x++) {
            for (int y = -RADIUS; y < RADIUS; y++) {
                Chunk chunk = getChunk(chunkPos.translate(x, y));
                viewable.add(chunk);
            }
        }
        return viewable;
    }

    /**
     * @return A stream over every single chunk.
     */
    public Stream<Chunk> stream() {
        return StreamSupport.stream(spliterator(), false);
    }
}
