package io.luna.game.model.mob.combat.damage;

import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Player;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Records how much damage each player has dealt to a single {@link Mob}, to decide who killed it.
 * <p>
 * Only players are recorded, by username hash, so no other mob is kept alive by this record and a player who logs back
 * in keeps their progress towards the kill. Each hit counts up to the hitpoints the mob had left. The record is cleared
 * when the mob dies and whenever its hitpoints are back to full.
 *
 * @author lare96
 */
public final class CombatDamageStack {

    /**
     * The mob this record belongs to.
     */
    private final Mob mob;

    /**
     * The damage dealt by each player, by username hash, in the order they first hit the mob. Created by the first
     * hit and dropped when cleared, so a mob that is not being attacked holds no record. Most mobs die to one player,
     * so it starts small.
     */
    private Map<Long, Integer> damageByPlayer;

    /**
     * Creates a new {@link CombatDamageStack}.
     *
     * @param mob The mob this record belongs to.
     */
    public CombatDamageStack(Mob mob) {
        this.mob = mob;
    }

    /**
     * Records {@code damage} if a player dealt it. Called before the damage is applied, so the killing hit counts.
     *
     * @param damage The hit about to be applied to the mob.
     */
    public void push(CombatDamage damage) {
        Mob attacker = damage.getAttacker();
        if (!(attacker instanceof Player) || attacker == mob) {
            return;
        }
        int amount = Math.min(damage.getRawAmount(), mob.getHealth());
        if (amount < 1) {
            return;
        }
        if (damageByPlayer == null) {
            damageByPlayer = new LinkedHashMap<>(4);
        }
        damageByPlayer.merge(attacker.asPlr().getUsernameHash(), amount, Integer::sum);
    }

    /**
     * Clears every recorded player.
     */
    public void clear() {
        damageByPlayer = null;
    }

    /**
     * Returns the player who has dealt the most damage, if they are still online. On a tie, the player who hit the mob
     * first wins.
     *
     * @return The player, or {@code null} if no player has damaged the mob or that player is offline.
     */
    public Player getHighestDamage() {
        if (damageByPlayer == null) {
            return null;
        }
        long highest = 0;
        int highestDamage = 0;
        for (Map.Entry<Long, Integer> entry : damageByPlayer.entrySet()) {
            if (entry.getValue() > highestDamage) {
                highest = entry.getKey();
                highestDamage = entry.getValue();
            }
        }
        return highestDamage == 0 ? null : mob.getWorld().getPlayer(highest).orElse(null);
    }
}
