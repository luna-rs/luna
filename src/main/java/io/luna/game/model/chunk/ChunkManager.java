package io.luna.game.model.chunk;

import io.luna.LunaConstants;
import io.luna.game.model.EntityType;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A model that loads new chunks and manages chunks that have already been loaded.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class ChunkManager {

    /**
     * A value that determines how many layers of chunks will be loaded around the Player, when looking
     * for viewable mobs.
     */
    private static final int RADIUS = 2;

    /**
     * A concurrent map of loaded chunks.
     */
    private final Map<ChunkPosition, Chunk> chunks = new ConcurrentHashMap<>();

    /**
     * Retrieves a chunk based on the argued chunk position, constructing and loading a new
     * one if needed.
     *
     * @param position The position to construct a new chunk with.
     * @return The existing or newly loaded chunk.
     */
    public Chunk getChunk(ChunkPosition position) {
        return chunks.computeIfAbsent(position, Chunk::new);
    }

    /**
     * A shortcut to {@link #getViewableMobs(Player, EntityType)} for type {@code PLAYER}.
     */
    public Set<Player> getViewablePlayers(Player player) {
        return getViewableMobs(player, EntityType.PLAYER);
    }

    /**
     * A shortcut to {@link #getViewableMobs(Player, EntityType)} for type {@code NPC}.
     */
    public Set<Npc> getViewableNpcs(Player player) {
        return getViewableMobs(player, EntityType.NPC);
    }

    /**
     * Returns a set of viewable mobs from {@code position}, potentially ordered using
     * the {@link ChunkMobComparator}.
     *
     * @return A set of viewable mobs within this and surrounding chunks.
     */
    private <T extends Mob> Set<T> getViewableMobs(Player player, EntityType type) {
        ChunkPosition position = player.getPosition().getChunkPosition();
        Set<T> viewable = LunaConstants.STAGGERED_UPDATING ?
                new TreeSet<>(new ChunkMobComparator(player)) : new HashSet<>();

        // Load the (25) viewable chunks.
        for (int x = -RADIUS; x < RADIUS; x++) {
            for (int y = -RADIUS; y < RADIUS; y++) {
                // Perform lookup for chunk, iterate through mobs in chunk.
                Chunk chunk = getChunk(position.translate(x, y));
                Set<T> mobs = chunk.getAll(type);
                for (T inside : mobs) {
                    // If mob is viewable, add to viewable set.
                    if (inside.isViewable(player)) {
                        viewable.add(inside);
                    }
                }
            }
        }
        return viewable;
    }
}
