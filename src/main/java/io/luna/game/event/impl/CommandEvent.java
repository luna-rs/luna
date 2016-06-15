package io.luna.game.event.impl;

import io.luna.game.event.Event;
import io.luna.game.model.mobile.Player;
import io.luna.game.model.mobile.PlayerRights;
import io.luna.util.StringUtils;

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
     * The rights of the player using the command.
     */
    private final PlayerRights rights;

    /**
     * Creates a new {@link CommandEvent}.
     *
     * @param name The name of the command.
     * @param args The arguments of the command.
     * @param rights The rights of the player using the command.
     */
    public CommandEvent(String name, String[] args, PlayerRights rights) {
        this.name = name;
        this.args = args;
        this.rights = rights;
    }

    /**
     * Creates a new {@link CommandEvent}.
     *
     * @param name The name of the command.
     * @param rights The rights of the player using the command.
     */
    public CommandEvent(String name, PlayerRights rights) {
        this(name, StringUtils.EMPTY_ARRAY, rights);
    }

    @Override
    public boolean matches(Object... args) {
        checkState(args.length == 1 || args.length == 2, "args.length != 1 or 2");

        boolean nameEquals = Objects.equals(args[0], name);
        if (args.length == 1) {
            return nameEquals;
        }
        return nameEquals && Objects.equals(args[1], rights);
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
