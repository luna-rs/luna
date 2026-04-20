package api.combat.player.dsl

import api.combat.player.VoidCombatAttack
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.combat.attack.CombatAttack

/**
 * Registers a conditional player attack hook and the custom attack logic that should run when the condition matches.
 *
 * Instances of this class are created during combat DSL setup. The [filter] decides whether this hook applies for a
 * given player attack context. If it does, [attack] may supply a custom [CombatAttack]. Returning `null` from
 * [attack] allows normal attack handling to continue. To explicitly cancel the attack, the supplied attack function
 * should return [VoidCombatAttack] ([PlayerAttackCombatDataReceiver.nothing]).
 *
 * @param listeners The backing listener collection this filter should register itself into.
 * @param filter The condition that decides whether this hook should apply for a given attack context.
 * @author lare96
 */
class PlayerAttackCombatFilter(
    val listeners: ArrayList<PlayerAttackCombatFilter>,
    val filter: PlayerAttackCombatDataReceiver.() -> Boolean) {

    /**
     * The custom attack supplier executed when [filter] matches.
     *
     * Returning `null` means default combat logic should continue. Returning a non-null [CombatAttack] replaces the
     * default attack handling with the supplied attack instance.
     */
    var attack: PlayerAttackCombatDataReceiver.() -> CombatAttack<Player>? = { null }

    /**
     * Assigns the attack logic for this filter and registers this instance as an active listener.
     *
     * Returning `null` from [attackFunction] allows the default attack to proceed. To cancel the attack entirely,
     * return [VoidCombatAttack] ([PlayerAttackCombatDataReceiver.nothing]).
     *
     * @param attackFunction The custom attack logic to run when [filter] matches.
     */
    fun then(attackFunction: PlayerAttackCombatDataReceiver.() -> CombatAttack<Player>?) {
        attack = attackFunction
        listeners += this
    }
}