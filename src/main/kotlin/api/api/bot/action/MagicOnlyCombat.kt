package api.bot.action

import io.luna.game.model.mob.combat.attack.CombatAttack

/**
 * Retains a Magic training session's native attack restriction while it runs or is paused beneath another script.
 *
 * @author lare96
 */
interface MagicOnlyCombat {
    /**
     * Whether this session requires every attack to use a native combat spell.
     */
    val magicOnly: Boolean

    /**
     * Validates the actual attack before costs, damage or experience and clears unsafe combat state on rejection.
     */
    fun permitsMagicAttack(attack: CombatAttack<*>): Boolean
}
