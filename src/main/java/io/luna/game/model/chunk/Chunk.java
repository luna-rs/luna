package io.luna.game.model.chunk;

import io.luna.game.model.Entity;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.StationaryEntity;
import io.luna.game.model.StationaryEntity.UpdateType;
import io.luna.game.model.mob.Player;
import io.luna.game.model.object.GameObject;
import io.luna.net.msg.GameMessageWriter;
import io.luna.net.msg.out.GroupedEntityMessageWriter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A model containing entities and updates for those entities within a chunk.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class Chunk {

    /**
     * This chunk's position.
     */
    private final ChunkPosition position;

    /**
     * The repository of entities.
     */
    private final ChunkRepository repository = new ChunkRepository();

    /**
     * Creates a new {@link ChunkPosition}.
     *
     * @param position The chunk position.
     */
    Chunk(ChunkPosition position) {
        this.position = position;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof Chunk) {
            Chunk other = (Chunk) obj;
            return position.equals(other.position);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return position.hashCode();
    }

    /**
     * Forwards to {@link ChunkRepository#add(Entity)}.
     */
    public void add(Entity entity) {
        repository.add(entity);
    }

    /**
     * Forwards to {@link ChunkRepository#remove(Entity)}.
     */
    public void remove(Entity entity) {
        repository.remove(entity);
    }

    /**
     * Forwards to {@link ChunkRepository#setOf(EntityType)}.
     */
    public <E extends Entity> Set<E> getAll(EntityType type) {
        return repository.setOf(type);
    }

    /**
     * Returns a stream over {@code type} entities in this chunk.
     *
     * @param type The entity type.
     * @param <E> The type.
     * @return The stream.
     */
    public <E extends Entity> Stream<E> stream(EntityType type) {
        return (Stream<E>) getAll(type).stream();
    }

    /**
     * Returns an iterator over {@code type} entities in this chunk.
     *
     * @param type The entity type.
     * @param <E> The type.
     * @return The iterator.
     */
    public <E extends Entity> Iterator<E> iterator(EntityType type) {
        return (Iterator<E>) getAll(type).iterator();
    }

    /**
     * Sends the necessary updates required to display every entity for {@code player}.
     *
     * @param player The player to display updates for.
     */
    public void sendGroupedUpdate(Player player) {
        List<GameMessageWriter> messages = new ArrayList<>();
        for (Entity e : repository) {
            if (e.getType() == EntityType.OBJECT && !((GameObject) e).isDynamic()) {
                return;
            }
            if (e instanceof StationaryEntity) {
                var entity = (StationaryEntity) e;
                Optional<Player> updatePlr = entity.getOwner();
                boolean isUpdate = updatePlr.isEmpty() || updatePlr.get().equals(player);
                if (isUpdate) {
                    messages.add(entity.sendUpdateMessage(player, UpdateType.SHOW, false));
                }
            }
        }
        player.queue(new GroupedEntityMessageWriter(player.getLastRegion(), this, messages));
    }

    /**
     * @return The position.
     */
    public ChunkPosition getPosition() {
        return position;
    }
}