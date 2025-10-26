package io.luna.game.event.impl;

import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.inter.TextInputInterface;

/**
 * A {@link PlayerEvent} implementation sent when a player enters text on a {@link TextInputInterface}.
 *
 * @author lare96
 */
public final class TextInputEvent extends PlayerEvent {

    /**
     * The entered text.
     */
    private final String text;

    /**
     * Creates a new {@link TextInputEvent}.
     *
     * @param plr The player.
     * @param text The entered text.
     */
    public TextInputEvent(Player plr, String text) {
        super(plr);
        this.text = text;
    }

    /**
     * @return The entered text.
     */
    public String getText() {
        return text;
    }
}
