package io.luna.game.model;

import io.luna.game.model.mobile.Npc;
import io.luna.game.model.mobile.Player;

/**
 * A collection of constants related to {@link Entity}s.
 * 
 * @author lare96 <http://github.org/lare96>
 */
public final class EntityConstants {

    /**
     * The default starting {@link Position} of all {@link Entity}s.
     */
    public static final Position DEFAULT_POSITION = new Position(3222, 3222);

    /**
     * The maximum distance that a {@link Player} can view.
     */
    public static final int VIEWING_DISTANCE = 15;

    /**
     * The maximum amount of active {@link Player}s that the protocol supports.
     */
    public static final int MAXIMUM_PLAYERS = 2048;

    /**
     * The maximum amount of active {@link Npc}s that the protocol supports.
     */
    public static final int MAXIMUM_NPCS = 16384;

    /**
     * A private constructor to discourage external instantiation.
     */
    private EntityConstants() {}
}
