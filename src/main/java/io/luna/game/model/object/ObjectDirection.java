package io.luna.game.model.object;

import com.google.common.collect.ImmutableMap;

/**
 * An enumerated type whose elements represent different object directions.
 *
 * @author lare96 
 */
public enum ObjectDirection {
    NORTH(1),
    SOUTH(3),
    EAST(2),
    WEST(0);

    /**
     * A map of object direction identifiers to {@link ObjectDirection} types.
     */
    public static final ImmutableMap<Integer, ObjectDirection> ALL;

    static {
        ImmutableMap.Builder<Integer, ObjectDirection> map = ImmutableMap.builder();
        for(ObjectDirection next: ObjectDirection.values()) {
            map.put(next.id, next);
        }
        ALL = map.build();
    }
    /**
     * The direction identifier.
     */
    private final int id;

    /**
     * Creates a new {@link ObjectDirection}.
     *
     * @param id The direction identifier.
     */
    ObjectDirection(int id) {
        this.id = id;
    }

    /**
     * @return The direction identifier.
     */
    public final int getId() {
        return id;
    }

}