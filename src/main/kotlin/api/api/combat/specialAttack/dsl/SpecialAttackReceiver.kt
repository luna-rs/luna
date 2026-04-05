package api.combat.specialAttack.dsl

import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.combat.attack.CombatAttack
import io.luna.game.model.mob.combat.damage.CombatDamageType
import io.luna.game.model.mob.combat.state.PlayerCombatContext

/**
 * DSL receiver used to configure a special attack definition.
 *
 * This receiver stores the special attack drain amount, execution flags, combat modifiers, and the optional custom
 * attack supplier used to build the final combat attack.
 *
 * @param drain The amount of special attack energy to consume.
 * @param activationOnly `true` if this special only performs an activation effect and does not replace the normal
 * combat attack, otherwise `false`.
 * @author lare96
 */
class SpecialAttackReceiver(val drain: Int, val activationOnly: Boolean) {

    /**
     * Optional maximum hit override for the special attack.
     *
     * When set, this value is used instead of the player's normal maximum hit calculation.
     */
    var maxHit: Int? = null

    /**
     * Accuracy multiplier applied to the special attack.
     *
     * A value of `1.0` means no accuracy change.
     */
    var attackBonus: Double = 1.0

    /**
     * Damage multiplier applied to the special attack.
     *
     * A value of `1.0` means no damage change.
     */
    var damageBonus: Double = 1.0

    /**
     * Indicates whether this special attack should execute instantly.
     *
     * Instant specials typically bypass the normal queued attack timing.
     */
    var instant: Boolean = false

    /**
     * The combat damage type used by this special attack.
     *
     * Defaults to [CombatDamageType.MELEE].
     */
    var type: CombatDamageType = CombatDamageType.MELEE

    /**
     * Supplies the custom combat attack used by this special attack.
     *
     * The receiver is the attacking player's combat context and the parameter is the current victim. Returning `null`
     * indicates that no custom attack was supplied.
     */
    var attack: PlayerCombatContext.(Mob) -> CombatAttack<Player>? = { null }

    /**
     * Sets the custom combat attack supplier for this special attack.
     *
     * @param attackSupplier The function used to build the special attack for the current victim.
     */
    fun attack(attackSupplier: PlayerCombatContext.(Mob) -> CombatAttack<Player>?) {
        attack = attackSupplier
    }
}