package io.luna.game.event.impl;

import io.luna.game.event.EventArguments;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.PlayerRights;
import io.luna.util.StringUtils;

/**
 * An event sent when a player activates a command.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class CommandEvent extends PlayerEvent {

    /**
     * The command name.
     */
    private final String name;

    /**
     * The command arguments.
     */
    private final String[] args;

    /**
     * Creates a new {@link CommandEvent}.
     *
     * @param player The player.
     * @param name The command name.
     * @param args The command arguments.
     */
    public CommandEvent(Player player, String name, String[] args) {
        super(player);
        this.name = name;
        this.args = args;
    }

    /**
     * Creates a new {@link CommandEvent}.
     *
     * @param name The command name.
     */
    public CommandEvent(Player player, String name) {
        this(player, name, StringUtils.EMPTY_ARRAY);
    }

    @Override
    public boolean matches(EventArguments args) {
        PlayerRights rights = plr.getRights();
        return args.equals(0, name) && rights.equalOrGreater((PlayerRights) args.get(1));
    }

    /**
     * Returns the command argument at {@code index}.
     */
    public String args(int index) {
        return args[index];
    }

    /**
     * @return The command name.
     */
    public String name() {
        return name;
    }

    /**
     * @return The command arguments.
     */
    public String[] args() {
        return args;
    }
}
