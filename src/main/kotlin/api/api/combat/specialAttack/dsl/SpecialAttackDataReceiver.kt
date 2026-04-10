package api.combat.specialAttack.dsl

import api.predef.*
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.combat.attack.CombatAttack
import io.luna.game.model.mob.combat.damage.CombatDamageType

/**
 * Mutable DSL configuration used to define a special attack.
 *
 * This receiver stores the static properties of a special attack, such as its energy drain, default combat type,
 * accuracy and damage modifiers, optional max-hit override, and whether the attack should behave as an instant or
 * activation-only special.
 *
 * It also stores the callback hooks used by the special-attack system:
 * - [attackTransformer] builds the combat attack used when the special executes.
 * - [launchedTransformer] runs after the special successfully launches.
 * - [arrivedConsumer] runs when the special hit arrives on the victim.
 *
 * @property drain The amount of special attack energy drained when this special is used.
 * @property damageType The combat damage type used by this special attack.
 * @property instant `true` if this is an instant special attack that should not add normal attack delay.
 * @property attackBonus A flat bonus applied to the special attack's accuracy roll.
 * @property damageBonus A percentage-based modifier applied to the special attack's damage.
 * @property maxHit An optional base max-hit override for this special attack.
 * @property activationOnly `true` if this special only performs activation logic and does not behave like a normal attack.
 * @author lare96
 */
class SpecialAttackDataReceiver(
    val drain: Int,
    val damageType: CombatDamageType = CombatDamageType.MELEE,
    val instant: Boolean = false,
    val attackBonus: Double = 0.0,
    val damageBonus: Double = 0.0,
    val maxHit: Int? = null,
    val activationOnly: Boolean
) {

    /**
     * Builds the combat attack instance that should be executed for this special attack.
     *
     * By default, this selects a standard melee or ranged attack based on [damageType].
     */
    var attackTransformer: SpecialAttackBuilderReceiver.() -> CombatAttack<Player>? = {
        when (damageType) {
            CombatDamageType.MELEE -> melee()
            CombatDamageType.MAGIC -> melee() // TODO Throw exception, or melee based magic attack?
            CombatDamageType.RANGED -> ranged()
        }
    }

    /**
     * Callback invoked after the special attack has successfully launched.
     *
     * The callback runs in a [SpecialAttackLaunchedReceiver] context containing the attacker and current victim.
     */
    var launchedTransformer: SpecialAttackLaunchedReceiver.() -> Unit = {}

    /**
     * Callback invoked when the special attack hit arrives on the victim.
     *
     * The callback runs in a [SpecialAttackArrivedReceiver] context containing the attacker, victim, and
     * resolved damage.
     */
    var arrivedConsumer: SpecialAttackArrivedReceiver.() -> Unit = {}

    /**
     * Replaces the combat-attack builder used for this special attack.
     *
     * @param attackTransformer The function used to create the combat attack instance.
     */
    fun attack(attackTransformer: SpecialAttackBuilderReceiver.() -> CombatAttack<Player>) {
        this.attackTransformer = attackTransformer
    }

    /**
     * Sets the callback that runs after the special attack has launched.
     *
     * The current receiver context contains the attacking player and the victim that the special was launched against.
     *
     * @param launchedTransformer The callback to run after launch.
     */
    fun launched(launchedTransformer: SpecialAttackLaunchedReceiver.() -> Unit) {
        this.launchedTransformer = launchedTransformer
    }

    /**
     * Sets the callback that runs when the special attack hit arrives.
     *
     * The current receiver context contains the attacking player, the victim, and the resolved damage that arrived
     * on impact.
     *
     * @param arrivedConsumer The callback to run when the hit arrives.
     */
    fun arrived(arrivedConsumer: SpecialAttackArrivedReceiver.() -> Unit) {
        this.arrivedConsumer = arrivedConsumer
    }
}