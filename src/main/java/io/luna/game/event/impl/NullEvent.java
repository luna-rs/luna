package io.luna.game.event.impl;

import io.luna.game.event.Event;
import io.luna.game.model.mob.Player;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

/**
 * A dummy {@link Event} type that is only used when no event is created from a {@link GameMessageReader}.
 *
 * @author lare96
 */
public final class NullEvent extends Event {

    /**
     * A singleton instance of this class, should be used instead of {@code null} when there is no valid event to
     * return from {@link GameMessageReader#decode(Player, GameMessage)}.
     */
    public static final NullEvent INSTANCE = new NullEvent();

    /**
     * Private constructor to discourage instantiation.
     */
    private NullEvent() {
    }
}
