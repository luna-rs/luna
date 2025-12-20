package io.luna.game.model.mob;

/**
 * Represents the spellbook currently active for a player.
 *
 * @author lare96
 */
public enum Spellbook {

    /**
     * The standard spellbook.
     */
    REGULAR(1151),

    /**
     * The ancient spellbook.
     */
    ANCIENT(12855);

    /**
     * The root widget id for this spellbook's spell interface.
     */
    private final int widgetId;

    /**
     * Creates a new {@link Spellbook}.
     *
     * @param widgetId The root widget id used by the client for this spellbook.
     */
    Spellbook(int widgetId) {
        this.widgetId = widgetId;
    }

    /**
     * Returns the root widget id for this spellbook's interface.
     *
     * @return The widget id.
     */
    public int getWidgetId() {
        return widgetId;
    }
}
