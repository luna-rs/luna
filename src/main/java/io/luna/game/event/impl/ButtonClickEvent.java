package io.luna.game.event.impl;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import io.luna.game.model.mob.Player;

/**
 * An event sent when a player clicks a button on an interface.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class ButtonClickEvent extends PlayerEvent {

    /**
     * The clicked button.
     */
    private final int id;

    /**
     * Creates a new {@link ButtonClickEvent}.
     *
     * @param player The player.
     * @param id The clicked button.
     */
    public ButtonClickEvent(Player player, int id) {
        super(player);
        this.id = id;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", id)
                .toString();
    }

    /**
     * @return The clicked button.
     */
    public int getId() {
        return id;
    }
}
