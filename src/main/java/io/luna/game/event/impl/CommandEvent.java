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
     * Retrieves the argument at {@code index} as an integer.
     *
     * @param index The index.
     * @return The converted integer.
     */
    public int asInt(int index) {
        return Integer.parseInt(args[index]);
    }

    /**
     * Replaces a character of the argument at {@code index}.
     *
     * @param index The index.
     * @param oldChar The character to replace.
     * @param newChar The character to replace with.
     * @return The argument, with the characters replaced.
     */
    public String replace(int index, char oldChar, char newChar) {
        return args[index].replace(oldChar, newChar);
    }

    /**
     * @return The command name.
     */
    public String getName() {
        return name;
    }

    /**
     * @return The command arguments.
     */
    public String[] getArgs() {
        return args;
    }
}
