package io.luna.game.event.impl;

import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.overlay.GameTabSet.TabIndex;

/**
 * Lets content repopulate a default sidebar interface after it is assigned to its tab.
 *
 * @author lare96
 */
public final class DefaultTabOpenedEvent extends PlayerEvent {

    private final TabIndex tab;

    public DefaultTabOpenedEvent(Player player, TabIndex tab) {
        super(player);
        this.tab = tab;
    }

    public TabIndex getTab() {
        return tab;
    }
}
