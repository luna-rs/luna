package io.luna.game.event.impl;

import io.luna.game.event.EventArguments;
import io.luna.game.event.IdBasedEvent;
import io.luna.game.model.mob.Player;

/**
 * An object-click based event. Not intended for interception.
 *
 * @author lare96 <http://github.org/lare96>
 */
public class ObjectClickEvent extends PlayerEvent implements IdBasedEvent {

    /**
     * An event sent when a player clicks an object's first index.
     *
     * @author lare96 <http://github.org/lare96>
     */
    public static final class ObjectFirstClickEvent extends ObjectClickEvent {

        /**
         * Creates a new {@link ObjectFirstClickEvent}.
         */
        public ObjectFirstClickEvent(Player player, int id, int x, int y) {
            super(player, id, x, y);
        }
    }

    /**
     * An event sent when a player clicks an object's second index.
     *
     * @author lare96 <http://github.org/lare96>
     */
    public static final class ObjectSecondClickEvent extends ObjectClickEvent {

        /**
         * Creates a new {@link ObjectSecondClickEvent}.
         */
        public ObjectSecondClickEvent(Player player, int id, int x, int y) {
            super(player, id, x, y);
        }
    }

    /**
     * An event sent when a player clicks an object's third index.
     *
     * @author lare96 <http://github.org/lare96>
     */
    public static final class ObjectThirdClickEvent extends ObjectClickEvent {

        /**
         * Creates a new {@link ObjectThirdClickEvent}.
         */
        public ObjectThirdClickEvent(Player player, int id, int x, int y) {
            super(player, id, x, y);
        }
    }

    /**
     * The object identifier.
     */
    private final int id;

    /**
     * The object's x coordinate.
     */
    private final int x;

    /**
     * The object's y coordinate.
     */
    private final int y;

    /**
     * Creates a new {@link ObjectClickEvent}.
     *
     * @param player The player.
     * @param id The object identifier.
     * @param x The object's x coordinate.
     * @param y The object's y coordinate.
     */
    private ObjectClickEvent(Player player, int id, int x, int y) {
        super(player, id);
        this.id = id;
        this.x = x;
        this.y = y;
    }

    @Override
    public final boolean matches(EventArguments args) {
        return args.contains(id);
    }

    /**
     * @return The object identifier.
     */
    public final int id() {
        return id;
    }

    /**
     * @return The object's x coordinate.
     */
    public final int x() {
        return x;
    }

    /**
     * @return The object's y coordinate.
     */
    public final int y() {
        return y;
    }
}
