package io.luna.game.event.impl;

import io.luna.game.event.Event;

import java.util.Arrays;
import java.util.Objects;

/**
 * An event implementation sent whenever a player picks up an item.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class PickupItemEvent extends Event {

    /**
     * The {@code x} coordinate of the item.
     */
    private final int x;

    /**
     * The {@code y} coordinate of the item.
     */
    private final int y;

    /**
     * The identifier for the item.
     */
    private final int id;

    /**
     * Creates a new {@link PickupItemEvent}.
     *
     * @param x The {@code x} coordinate of the item.
     * @param y The {@code y} coordinate of the item.
     * @param id The identifier for the item.
     */
    public PickupItemEvent(int x, int y, int id) {
        this.x = x;
        this.y = y;
        this.id = id;
    }

    @Override
    public boolean matches(Object... args) {
        return Arrays.stream(args).anyMatch(it -> Objects.equals(it, id));
    }

    /**
     * @return The {@code x} coordinate of the item.
     */
    public int getX() {
        return x;
    }

    /**
     * @return The {@code y} coordinate of the item.
     */
    public int getY() {
        return y;
    }

    /**
     * @return The identifier for the item.
     */
    public int getId() {
        return id;
    }
}
