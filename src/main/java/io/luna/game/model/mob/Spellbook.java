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
    REGULAR(1151, 1829),

    /**
     * The ancient spellbook.
     */
    ANCIENT(12855, 1689);

    /**
     * The root widget id for this spellbook's spell interface.
     */
    private final int defaultWidget;

    /**
     * The root widget id for this spellbook's auto-cast interface.
     */
    private final int autocastWidget;

    /**
     * Creates a new {@link Spellbook}.
     *
     * @param defaultWidget The root widget id.
     * @param autocastWidget The auto-cast widget id.
     */
    Spellbook(int defaultWidget, int autocastWidget) {
        this.defaultWidget = defaultWidget;
        this.autocastWidget = autocastWidget;
    }

    /**
     * @return The widget id.
     */
    public int getDefaultWidget() {
        return defaultWidget;
    }

    /**
     * @return The auto-cast widget id.
     */
    public int getAutocastWidget() {
        return autocastWidget;
    }
}