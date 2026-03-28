package io.luna.game.event.impl;

import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.combat.damage.CombatDamage;

/**
 * An event fired after a {@link Player} receives a {@link CombatDamage} hit.
 * <p>
 * This event provides access to the resolved damage instance that was received by the player, allowing listeners to
 * inspect the attacker, damage type, and final amount.
 *
 * @author lare96
 */
public final class CombatDamageReceivedEvent extends PlayerEvent {

    /**
     * The combat damage received by the player.
     */
    private final CombatDamage damage;

    /**
     * Creates a new {@link CombatDamageReceivedEvent}.
     *
     * @param plr The player that received the hit.
     * @param damage The resolved combat damage that was received.
     */
    public CombatDamageReceivedEvent(Player plr, CombatDamage damage) {
        super(plr);
        this.damage = damage;
    }

    /**
     * @return The received combat damage.
     */
    public CombatDamage getDamage() {
        return damage;
    }
}