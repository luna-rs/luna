package io.luna.game.event.impl;

import io.luna.game.model.mob.Player;

/**
 * A {@link PlayerEvent} implementation sent when the player clicks to continue a dialogue.
 *
 * @author lare96
 */
public final class ContinueDialogueEvent extends PlayerEvent {

    /**
     * The widget id that was clicked.
     */
    private final int widgetId;

    /**
     * Creates a new {@link PlayerEvent}.
     *
     * @param plr The player.
     * @param widgetId The widget id that was clicked.
     */
    public ContinueDialogueEvent(Player plr, int widgetId) {
        super(plr);
        this.widgetId = widgetId;
    }

    /**
     * @return The widget id that was clicked.
     */
    public int getWidgetId() {
        return widgetId;
    }
}
