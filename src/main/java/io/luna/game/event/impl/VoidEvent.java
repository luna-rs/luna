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
public final class VoidEvent extends Event {

    /**
     * A singleton instance of this class, should be used instead of {@code null} when there is no event to
     * return from {@link GameMessageReader#decode(Player, GameMessage)}.
     */
    public static final VoidEvent INSTANCE = new VoidEvent();

    /**
     * Private constructor to discourage instantiation.
     */
    private VoidEvent() {
    }
}
