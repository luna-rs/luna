package io.luna.game.cache.map;

import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

public final class MapObjectSet implements Iterable<MapObject> {

    private final ImmutableList<MapObject> objects;

    public MapObjectSet(ImmutableList<MapObject> objects) {
        this.objects = objects;
    }

    public ImmutableList<MapObject> getObjects() {
        return objects;
    }

    @NotNull
    @Override
    public Iterator<MapObject> iterator() {
        return objects.iterator();
    }
}
