package io.luna.game.event.impl;

import io.luna.game.event.Event;

import java.util.Arrays;
import java.util.Objects;

/**
 * An event implementation sent whenever a player clicks a button on an interface.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class ButtonClickEvent extends Event {

    /**
     * The identifier for the button that was clicked.
     */
    private final int id;

    /**
     * Creates a new {@link ButtonClickEvent}.
     *
     * @param id The identifier for the button that was clicked.
     */
    public ButtonClickEvent(int id) {
        this.id = id;
    }

    @Override
    public boolean matches(Object... args) {
        return Arrays.stream(args).anyMatch(it -> Objects.equals(it, id));
    }

    /**
     * @return The identifier for the button that was clicked.
     */
    public int getId() {
        return id;
    }
}
