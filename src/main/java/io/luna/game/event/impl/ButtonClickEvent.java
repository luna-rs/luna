package io.luna.game.event.impl;

import io.luna.game.event.Event;
import io.luna.game.model.mobile.Player;

import java.util.Objects;

import static com.google.common.base.Preconditions.checkState;

/**
 * An {@link Event} implementation sent whenever a {@link Player} clicks a button on an interface.
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
        checkState(args.length == 1, "args.length != 1");
        return Objects.equals(args[0], id);
    }

    /**
     * @return The identifier for the button that was clicked.
     */
    public int getId() {
        return id;
    }
}
