package io.luna.game.event.impl;

import io.luna.game.event.Event;
import io.luna.game.model.mobile.Player;

import java.util.Objects;

import static com.google.common.base.Preconditions.checkState;

/**
 * An {@link Event} implementation sent whenever a {@link Player} types a command.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class CommandEvent extends Event {

    /**
     * The name of the command.
     */
    private final String name;

    /**
     * The arguments of the command.
     */
    private final String[] args;

    /**
     * Creates a new {@link CommandEvent}.
     *
     * @param name The name of the command.
     * @param args The arguments of the command.
     */
    public CommandEvent(String name, String[] args) {
        this.name = name;
        this.args = args;
    }

    /**
     * Creates a new {@link CommandEvent}.
     *
     * @param name The name of the command.
     */
    public CommandEvent(String name) {
        this(name, new String[] {});
    }

    @Override
    public boolean matches(Object... args) {
        checkState(args.length == 1, "args.length != 1");
        return Objects.equals(args[0], name);
    }

    /**
     * @return The name of the command.
     */
    public String getName() {
        return name;
    }

    /**
     * @return The arguments of the command.
     */
    public String[] getArgs() {
        return args;
    }
}
