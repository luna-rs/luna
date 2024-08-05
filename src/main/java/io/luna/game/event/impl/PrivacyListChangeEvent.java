package io.luna.game.event.impl;

import io.luna.game.model.mob.Player;

/**
 * A privacy list change based event. Not intended for interception.
 *
 * @author lare96
 */
public class PrivacyListChangeEvent extends PlayerEvent implements ControllableEvent {

    /**
     * An event sent when the player adds someone to their friends' list.
     */
    public static final class AddFriendEvent extends PrivacyListChangeEvent {

        /**
         * Creates a new {@link AddFriendEvent}.
         *
         * @param plr The player.
         * @param name The name of the player to update in the list.
         */
        public AddFriendEvent(Player plr, long name) {
            super(plr, name);
        }
    }

    /**
     * An event sent when the player adds someone to their ignore list.
     */
    public static final class AddIgnoreEvent extends PrivacyListChangeEvent {

        /**
         * Creates a new {@link AddIgnoreEvent}.
         *
         * @param plr The player.
         * @param name The name of the player to update in the list.
         */
        public AddIgnoreEvent(Player plr, long name) {
            super(plr, name);
        }
    }

    /**
     * An event sent when the player adds someone to their friends' list.
     */
    public static final class RemoveFriendEvent extends PrivacyListChangeEvent {

        /**
         * Creates a new {@link RemoveFriendEvent}.
         *
         * @param plr The player.
         * @param name The name of the player to update in the list.
         */
        public RemoveFriendEvent(Player plr, long name) {
            super(plr, name);
        }
    }

    /**
     * An event sent when the player removes someone from their ignore list.
     */
    public static final class RemoveIgnoreEvent extends PrivacyListChangeEvent {

        /**
         * Creates a new {@link RemoveIgnoreEvent}.
         *
         * @param plr The player.
         * @param name The name of the player to update in the list.
         */
        public RemoveIgnoreEvent(Player plr, long name) {
            super(plr, name);
        }
    }

    /**
     * The name of the player to update in the list.
     */
    private final long name;

    /**
     * Creates a new {@link PrivacyListChangeEvent}.
     *
     * @param plr The player.
     * @param name The name of the player to update in the list.
     */
    private PrivacyListChangeEvent(Player plr, long name) {
        super(plr);
        this.name = name;
    }

    /**
     * @return The name of the player to update in the list.
     */
    public long getName() {
        return name;
    }
}
