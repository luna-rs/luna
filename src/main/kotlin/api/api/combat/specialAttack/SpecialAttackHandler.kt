package api.combat.specialAttack

import api.combat.specialAttack.dsl.SpecialAttackDataReceiver
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.combat.SpecialAttackType
import io.luna.game.model.mob.combat.damage.CombatDamageType
import io.luna.game.model.mob.combat.state.PlayerCombatContext

/**
 * Central registry and entry point for special attack definitions.
 *
 * This object stores all registered special attacks by [SpecialAttackType] and exposes helpers for:
 * - Handling special attack activation from the player
 * - Registering combat special attacks
 * - Registering activation-only special attacks
 * - Retrieving special attack data for a player's current weapon
 */
object SpecialAttackHandler {

    /**
     * All registered special attacks keyed by their weapon special attack type.
     */
    private val specialAttacks = HashMap<SpecialAttackType, SpecialAttackDataReceiver>()

    /**
     * Handles manual special attack activation for the specified player.
     *
     * This resolves the player's current weapon [SpecialAttackType], checks whether a matching special attack is
     * registered, and verifies that the player has enough special attack energy to use it.
     *
     * For activation-based special attacks, this also locks the special attack bar and submits a
     * [SpecialActivationAction] so the special can be performed.
     *
     * @param plr The player attempting to activate a special attack.
     * @return `true` if a matching special attack was found and activation may proceed, otherwise `false`.
     */
    fun handleActivated(plr: Player): Boolean {
        val type = plr.combat.weapon.specialAttackType
        val receiver = specialAttacks[type] ?: return false

        val specialBar = plr.combat.specialBar
        if (!specialBar.checkEnergy(receiver.drain)) {
            return false
        }

        if (receiver.activationOnly) {
            // Non-combat special attacks activate after a small delay.
            specialBar.isLocked = true
            plr.actions.submitIfAbsent(SpecialActivationAction(plr, receiver))
        }
        // TODO Instant special attack queuing? Test behaviour in game.
        return true
    }

    /**
     * Registers a combat special attack definition for the specified [SpecialAttackType].
     *
     * The supplied [action] configures a mutable [SpecialAttackDataReceiver], which is then stored and used whenever
     * a player activates the corresponding weapon special attack.
     *
     * If another special attack is already registered for the same [type], it is replaced.
     *
     * @param type The weapon special attack type to register.
     * @param drain The amount of special attack energy drained when used.
     * @param damageType The combat damage type used by this special attack.
     * @param instant `true` if this special should behave as an instant special attack.
     * @param attackBonus A flat bonus applied to the special attack's accuracy.
     * @param damageBonus A percentage-based bonus applied to damage.
     * @param maxHit An optional base max-hit override.
     * @param action The DSL block used to configure the special attack.
     */
    fun attack(type: SpecialAttackType,
               drain: Int,
               damageType: CombatDamageType = CombatDamageType.MELEE,
               instant: Boolean = false,
               attackBonus: Double = 0.0,
               damageBonus: Double = 0.0,
               maxHit: Int? = null,
               action: SpecialAttackDataReceiver.() -> Unit) {
        val receiver = SpecialAttackDataReceiver(drain, damageType, instant, attackBonus, damageBonus, maxHit, false)
        action(receiver)
        specialAttacks[type] = receiver
    }

    /**
     * Registers an activation-only special attack for the specified [SpecialAttackType].
     *
     * Activation-only specials do not expose the normal combat special attack modifiers. Instead, they execute the
     * supplied [action] directly from the launched callback using the activating [Player] as the receiver.
     *
     * If another special attack is already registered for the same [type], it is replaced.
     *
     * @param type The weapon special attack type to register.
     * @param drain The amount of special attack energy drained when used.
     * @param action The logic to execute when the activation-only special is triggered.
     */
    fun activation(type: SpecialAttackType, drain: Int, action: Player.() -> Unit) {
        // Hide special attack modulators from activation specials.
        val receiver = SpecialAttackDataReceiver(drain = drain,
                                                 activationOnly = true)
        receiver.launchedTransformer = { action(attacker) }
        specialAttacks[type] = receiver
    }

    /**
     * Gets the registered special attack data for this combat context's current weapon.
     *
     * @return The registered [SpecialAttackDataReceiver] for the current weapon.
     * @throws IllegalArgumentException if no special attack is registered for the weapon's
     * current [SpecialAttackType].
     */
    fun PlayerCombatContext.specialAttackData(): SpecialAttackDataReceiver {
        val type = weapon.specialAttackType
        return requireNotNull(specialAttacks[type]) { " No receiver for special attack type $type." }
    }
}