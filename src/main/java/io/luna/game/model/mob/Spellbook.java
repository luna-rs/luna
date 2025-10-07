package io.luna.game.model.mob;

/**
 * Represents the different types of spellbooks in Runescape.
 *
 * @author lare96
 */
public enum Spellbook {
    REGULAR(1151),
    ANCIENT(12855);

    /**
     * The widget id.
     */
    private final int widgetId;

    /**
     * Creates a new {@link Spellbook}.
     *
     * @param widgetId The widget id.
     */
    Spellbook(int widgetId) {
        this.widgetId = widgetId;
    }

    /**
     * @return The widget id.
     */
    public int getWidgetId() {
        return widgetId;
    }
}
