package io.luna.game.model.def;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * An implementation of {@link DefinitionRepository} that is backed internally by a {@link LinkedHashMap}.
 */
public final class MapDefinitionRepository<V extends Definition> extends DefinitionRepository<V> {

    /**
     * A map of definitions.
     */
    private final Map<Integer, V> definitions = new LinkedHashMap<>();

    @Override
    void put(int id, V definition) {
        definitions.put(id, definition);
    }

    @Override
    public Optional<V> get(int id) {
        return Optional.ofNullable(definitions.get(id));
    }

    @Override
    public Iterator<V> newIterator() {
        return definitions.values().iterator();
    }
}
