package io.luna.game.model.mob.combat;

import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Player;

/**
 * An optional combat ownership rule for encounter mobs. Ordinary mobs impose no additional restriction.
 *
 * @author lare96
 */
public interface CombatRestriction {

    /**
     * Returns whether this encounter mob may exchange attacks and effects with the opponent.
     */
    boolean canFight(Mob opponent);

    /**
     * Checks both participants, including when an encounter NPC is the attacker or a secondary spell target.
     */
    static boolean permits(Mob attacker, Mob victim) {
        if ((attacker instanceof Player attackerPlayer && attackerPlayer.getControllers().getPrimary().isSpectator()) ||
                (victim instanceof Player victimPlayer && victimPlayer.getControllers().getPrimary().isSpectator())) {
            return false;
        }
        return (!(attacker instanceof CombatRestriction) || ((CombatRestriction) attacker).canFight(victim)) &&
                (!(victim instanceof CombatRestriction) || ((CombatRestriction) victim).canFight(attacker));
    }
}
