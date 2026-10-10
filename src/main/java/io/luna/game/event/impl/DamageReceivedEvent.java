package io.luna.game.event.impl;

import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.block.Hit;

/**
 * An event fired after a {@link Player} takes a hit from any source, including combat, skilling failures and
 * poison. The hit has already been taken from the player's hitpoints.
 *
 * @author TheLining
 */
public final class DamageReceivedEvent extends PlayerEvent {

    /**
     * The hit that was taken.
     */
    private final Hit hit;

    /**
     * Creates a new {@link DamageReceivedEvent}.
     *
     * @param plr The player that took the hit.
     * @param hit The hit that was taken.
     */
    public DamageReceivedEvent(Player plr, Hit hit) {
        super(plr);
        this.hit = hit;
    }

    /**
     * @return The hit that was taken.
     */
    public Hit getHit() {
        return hit;
    }
}
