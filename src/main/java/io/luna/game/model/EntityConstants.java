package io.luna.game.model;

/**
 * Holds constants related to entities.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class EntityConstants {

    /**
     * The amount of players that can login per tick.
     */
    public static final int LOGIN_THRESHOLD = 50;

    /**
     * The amount of players that can logout per tick.
     */
    public static final int LOGOUT_THRESHOLD = 50;

    /**
     * The maximum amount of tiles a player can view.
     */
    public static final int VIEWING_DISTANCE = 15;

    /**
     * A private constructor.
     */
    private EntityConstants() {
    }
}
