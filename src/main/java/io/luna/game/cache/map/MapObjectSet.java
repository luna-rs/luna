package io.luna.game.cache.map;

import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

/**
 * Represents a collection of all decoded {@link MapObject} types from the cache.
 *
 * @author lare96
 */
public final class MapObjectSet implements Iterable<MapObject> {

    /**
     * The {@link ImmutableList} of objects.
     */
    private final ImmutableList<MapObject> objects;

    /**
     * Creates a new {@link MapObjectSet}.
     *
     * @param objects The {@link ImmutableList} of objects.
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
     * @return The {@link ImmutableList} of objects.
     */
    public ImmutableList<MapObject> getObjects() {
        return objects;
    }


}
