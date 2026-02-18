package io.luna.game.cache.map;

import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

/**
 * Immutable collection of all decoded {@link MapObject} placements.
 * <p>
 * Backed by an {@link ImmutableList} for compact storage and fast iteration.
 *
 * @author lare96
 */
public final class MapObjectSet implements Iterable<MapObject> {

    /**
     * All decoded object placements.
     */
    private final ImmutableList<MapObject> objects;

    /**
     * Creates a new {@link MapObjectSet}.
     *
     * @param objects All decoded object placements.
     */
    public MapObjectSet(ImmutableList<MapObject> objects) {
        this.objects = objects;
    }

    @NotNull
    @Override
    public Iterator<MapObject> iterator() {
        return objects.iterator();
    }

    /**
     * @return The underlying immutable list of decoded object placements.
     */
    public ImmutableList<MapObject> getObjects() {
        return objects;
    }
}
