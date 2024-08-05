package io.luna.game.event.impl;

import io.luna.game.model.mob.Player;
import io.luna.util.StringUtils;

/**
 * An event sent when a player activates a command.
 *
 * @author lare96
 */
public final class CommandEvent extends PlayerEvent implements ControllableEvent {

    /**
     * The complete command string that was entered.
     */
    private final String completeString;

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
    public CommandEvent(Player player, String completeString, String name, String[] args) {
        super(player);
        this.completeString = completeString;
        this.name = name;
        this.args = args;
    }

    /**
     * Creates a new {@link CommandEvent}.
     *
     * @param name The command name.
     */
    public CommandEvent(Player player, String completeString, String name) {
        this(player, completeString, name, StringUtils.EMPTY_ARRAY);
    }

    /**
     * Retrieves the argument at {@code index} as an integer.
     *
     * @param index The index.
     * @return The converted integer.
     */
    public Integer asInt(int index) {
        try {
            return Integer.valueOf(args[index]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Gets the entire string after the argument {@code index}.
     *
     * @param index The index to start at.
     * @return The rest of the arguments, as one string.
     */
    public String getInputFrom(int index) {
        StringBuilder sb = new StringBuilder();
        for (int slot = 0; slot < args.length; slot++) {
            if (slot >= index) {
                sb.append(args[slot]).append(" ");
            }
        }
        sb.setLength(sb.length() - 1);
        return sb.toString();
    }

    /**
     * @return The complete command string that was entered.
     */
    public String getCompleteString() {
        return completeString;
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
