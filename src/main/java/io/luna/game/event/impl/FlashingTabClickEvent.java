package io.luna.game.event.impl;

import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.inter.GameTabSet.TabIndex;

/**
 * An event sent when a player clicks a flashing tab.
 *
 * @author lare96
 */
public final class FlashingTabClickEvent extends PlayerEvent {

    /**
     * The flashing tab that was clicked.
     */
    private final TabIndex tab;

    /**
     * Creates a new {@link FlashingTabClickEvent}.
     *
     * @param player The player.
     * @param tab The flashing tab that was clicked.
     */
    public FlashingTabClickEvent(Player player, TabIndex tab) {
        super(player);
        this.tab = tab;
    }

    /**
     * @return The flashing tab that was clicked.
     */
    public TabIndex getTab() {
        return tab;
    }
}
