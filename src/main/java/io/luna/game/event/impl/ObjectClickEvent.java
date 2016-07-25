package io.luna.game.event.impl;

import io.luna.game.event.Event;

import java.util.Objects;

import static com.google.common.base.Preconditions.checkState;

/**
 * An event implementation sent when a player clicks any object index.
 *
 * @author lare96 <http://github.org/lare96>
 */
class ObjectClickEvent extends Event {

    /**
     * The identifier for the clicked object.
     */
    private final int id;

    /**
     * The x coordinate of the object.
     */
    private final int x;

    /**
     * The y coordinate of the object.
     */
    private final int y;

    /**
     * Creates a new {@link ObjectClickEvent}.
     *
     * @param id The identifier for the clicked object.
     * @param x The x coordinate of the object.
     * @param y The y coordinate of the object.
     */
    ObjectClickEvent(int id, int x, int y) {
        this.id = id;
        this.x = x;
        this.y = y;
    }

    @Override
    public final boolean matches(Object... args) {
        checkState(args.length == 1, "args.length != 1");
        return Objects.equals(args[0], id);
    }

    /**
     * @return The identifier for the clicked object.
     */
    public final int getId() {
        return id;
    }

    /**
     * @return The x coordinate of the object.
     */
    public final int getX() {
        return x;
    }

    /**
     * @return The y coordinate of the object.
     */
    public final int getY() {
        return y;
    }
}
