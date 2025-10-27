package io.luna.game.event.impl;

import io.luna.game.model.mob.Player;

/**
 * A {@link PlayerEvent} sent when the player changes window focus.
 *
 * @author lare96
 */
public final class FocusChangedEvent extends PlayerEvent {

    /**
     * If the window is currently focused.
     */
    private final boolean focused;

    /**
     * Creates a new {@link PlayerEvent}.
     *
     * @param plr The player.
     * @param focused If the window is currently focused.
     */
    public FocusChangedEvent(Player plr, boolean focused) {
        super(plr);
        this.focused = focused;
    }

    /**
     * @return If the window is currently focused.
     */
    public boolean isFocused() {
        return focused;
    }
}
