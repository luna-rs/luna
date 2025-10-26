package io.luna.game.event.impl;

import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.inter.NumberInputInterface;

/**
 * A {@link PlayerEvent} implementation sent when a player enters a number on the {@link NumberInputInterface}.
 *
 * @author lare96
 */
public final class NumberInputEvent extends PlayerEvent {

    /**
     * The entered number.
     */
    private final int number;

    /**
     * Creates a new {@link NumberInputEvent}.
     *
     * @param plr The player.
     * @param number The entered number.
     */
    public NumberInputEvent(Player plr, int number) {
        super(plr);
        this.number = number;
    }

    /**
     * @return The entered number.
     */
    public int getNumber() {
        return number;
    }
}
