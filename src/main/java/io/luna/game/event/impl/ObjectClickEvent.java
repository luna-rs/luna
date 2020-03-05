package io.luna.game.event.impl;

import io.luna.game.model.mob.Player;
import io.luna.game.model.object.GameObject;

/**
 * An object-click based event. Not intended for interception.
 *
 * @author lare96 <http://github.org/lare96>
 */
public class ObjectClickEvent extends PlayerEvent {

    /**
     * An event sent when a player clicks an object's first index.
     *
     * @author lare96 <http://github.org/lare96>
     */
    public static final class ObjectFirstClickEvent extends ObjectClickEvent {

        /**
         * Creates a new {@link ObjectFirstClickEvent}.
         */
        public ObjectFirstClickEvent(Player player, GameObject gameObject) {
            super(player, gameObject);
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
        public ObjectSecondClickEvent(Player player, GameObject gameObject) {
            super(player, gameObject);
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
        public ObjectThirdClickEvent(Player player, GameObject gameObject) {
            super(player, gameObject);
        }
    }

    /**
     * The clicked object.
     */
    private final GameObject gameObject;

    /**
     * Creates a new {@link ObjectClickEvent}.
     *
     * @param player The player.
     * @param gameObject The clicked object.
     */
    private ObjectClickEvent(Player player, GameObject gameObject) {
        super(player);
        this.gameObject = gameObject;
    }

    /**
     * @return The object identifier.
     */
    public final int getId() {
        return gameObject.getId();
    }

    /**
     * @return The object's x coordinate.
     */
    public final int getX() {
        return gameObject.getPosition().getX();
    }

    /**
     * @return The object's y coordinate.
     */
    public final int getY() {
        return gameObject.getPosition().getY();
    }

    /**
     * @return The clicked object.
     */
    public GameObject getGameObject() {
        return gameObject;
    }
}
