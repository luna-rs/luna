package io.luna.game.model;

import com.google.common.collect.ImmutableList;

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
     * The wilderness areas.
     */
    public static final ImmutableList<Area> WILDERNESS = ImmutableList
        .of(Area.create(2941, 3518, 3392, 3966, 0), Area.create(2941, 3518, 3392, 3966, 1),
            Area.create(2941, 3518, 3392, 3966, 2), Area.create(2941, 3518, 3392, 3966, 3));

    /**
     * A private constructor.
     */
    private EntityConstants() {
    }
}
