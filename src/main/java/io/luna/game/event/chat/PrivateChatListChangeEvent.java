package io.luna.game.event.chat;

import io.luna.game.event.entity.player.PlayerEvent;
import io.luna.game.model.mob.Player;

/**
 * An event sent when a player's friend or ignore list changes.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class PrivateChatListChangeEvent extends PlayerEvent {

    /**
     * An enumerated type representing the change that was recorded.
     */
    public enum ChangeType {
        ADD_FRIEND,
        ADD_IGNORE,
        REMOVE_FRIEND,
        REMOVE_IGNORE
    }

    /**
     * The changed friend/ignore.
     */
    private final long name;

    /**
     * The type of change.
     */
    private final ChangeType type;

    /**
     * Creates a new {@link PrivateChatListChangeEvent}.
     *
     * @param player The player.
     * @param name The changed friend/ignore.
     * @param type The type of change.
     */
    public PrivateChatListChangeEvent(Player player, long name, ChangeType type) {
        super(player);
        this.name = name;
        this.type = type;
    }

    /**
     * @return The changed friend/ignore.
     */
    public long getName() {
        return name;
    }

    /**
     * @return The type of change.
     */
    public ChangeType getType() {
        return type;
    }
}