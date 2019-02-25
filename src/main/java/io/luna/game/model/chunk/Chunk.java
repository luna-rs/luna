package io.luna.game.model.chunk;

import io.luna.game.model.Entity;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;

import java.util.Iterator;
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
     * @return The position.
     */
    public ChunkPosition getPosition() {
        return position;
    }

    /**
     * @return This chunk's absolute position.
     */
    public Position getAbsolutePosition() {
        return new Position(position.getAbsX(), position.getAbsY());
    }
}