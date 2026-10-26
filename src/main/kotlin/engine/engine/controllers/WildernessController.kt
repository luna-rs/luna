package engine.controllers

import api.attr.Attr
import api.predef.ext.ticks
import engine.combat.status.StatusEffectType
import engine.combat.status.hooks.SkullStatusEffect
import engine.controllers.WildernessLocatableController.wildernessLevel
import game.skill.magic.teleportSpells.TeleportAction
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.controller.PlayerController

/**
 * A [PlayerController] that enforces wilderness-specific gameplay rules.
 *
 * This controller is registered while a player is inside the wilderness.
 * Player combat requires both participants to be inside the wilderness and limits their combat-level difference to
 * the shallower participant's wilderness level. Eligibility uses current positions rather than cached display levels.
 * Committed PvP attacks apply Lost City's twenty-minute skull unless either participant's recent aggression history
 * establishes retaliation. History belongs to the login session and survives controller changes; the skull itself
 * uses the existing persistent status effect and death-item protection rules.
 *
 * @author lare96
 */
class WildernessController(private val plr: Player) : PlayerController(plr) {

    /**
     * Session-only aggression state shared by successive Wilderness controller instances.
     *
     * @author lare96
     */
    companion object {
        /**
         * Lost City's pk_skull_duration, in 600ms game ticks.
         */
        private const val SKULL_TICKS = 2000

        /**
         * Non-persistent history, matching the temporary pk_prey and pk_predator variables. Identity tokens prevent
         * index reuse or a new login from inheriting another session's retaliation rights without retaining players.
         */
        private val Player.pvpHistory by Attr.obj { PvpHistory() }
    }

    /**
     * Lost City's two outgoing and three incoming attack slots. Every attack shifts the slots, including repeats;
     * this is not a list of distinct opponents or a timer. Only small identity tokens are retained.
     *
     * @author lare96
     */
    private class PvpHistory {
        /**
         * Identifies this login session without keeping its Player object alive in other histories.
         */
        val identity = Any()

        /**
         * The most recent two outgoing attacks, newest first.
         */
        private val targets = arrayOfNulls<Any>(2)

        /**
         * The most recent three incoming attacks, newest first.
         */
        private val aggressors = arrayOfNulls<Any>(3)

        /**
         * Checks retaliation before recording this attack in both participants' histories. A defender is exempt if
         * the opponent attacked them in either outgoing slot, or appears in any of their three incoming slots.
         *
         * @param victim The recipient's session history.
         * @return Whether this attack deserves a new skull or refreshes an existing one.
         */
        fun recordAttack(victim: PvpHistory): Boolean {
            val retaliation = victim.targets.any { it === identity } || aggressors.any { it === victim.identity }
            targets[1] = targets[0]
            targets[0] = victim.identity
            victim.aggressors[2] = victim.aggressors[1]
            victim.aggressors[1] = victim.aggressors[0]
            victim.aggressors[0] = identity
            return !retaliation
        }
    }

    override fun pvpAttackCommitted(victim: Player) {
        // Retribution comes from a dead player and must not start a fresh skull after death-item selection.
        if (plr === victim || !plr.isAlive || !victim.isAlive ||
            plr.state != EntityState.ACTIVE || victim.state != EntityState.ACTIVE) return
        if (plr.pvpHistory.recordAttack(victim.pvpHistory)) {
            plr.status.remove(StatusEffectType.SKULLED)
            plr.status.add(SkullStatusEffect(plr, SKULL_TICKS.ticks))
        }
    }

    override fun teleport(action: TeleportAction): Boolean {
        val maxLevel = action.maxWildernessLevel
        if (plr.wildernessLevel > maxLevel) {
            if (maxLevel >= 30) {
                plr.sendMessage("A mysterious force blocks your teleport.")
            } else {
                plr.sendMessage("A mysterious force blocks your teleport spell!")
            }
            plr.sendMessage("You can't use this teleport after level $maxLevel wilderness.")
            return false
        }
        return true
    }

    override fun combat(victim: Mob): Boolean {
        // Combat level checks not needed for NPCs.
        if (victim is Npc) {
            return true
        }

        // Do combat level checks for players.
        val combatLevel = plr.combatLevel
        val wildernessLevel = minOf(WildernessLocatableController.levelAt(plr.position),
                                    WildernessLocatableController.levelAt(victim.position))
        if (wildernessLevel < 1) {
            return false
        }
        val attackRange = combatLevel - wildernessLevel..combatLevel + wildernessLevel
        val attackable = attackRange.contains(victim.combatLevel)
        if (!attackable) {
            plr.sendMessage("Your level difference is too great!")
            plr.sendMessage("You need to move deeper into the Wilderness.")
            return false
        }
        return true
    }

    override fun move() {
        val newLevel = WildernessLocatableController.levelAt(plr.position)
        if (plr.wildernessLevel != newLevel) {
            plr.wildernessLevel = newLevel
            plr.sendText("@yel@Level: $newLevel", 199)
        }
    }
}
