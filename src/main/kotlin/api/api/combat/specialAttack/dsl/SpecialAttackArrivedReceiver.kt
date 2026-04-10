package api.combat.specialAttack.dsl

import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.combat.damage.CombatDamage

/**
 * Receiver passed to special-attack arrival callbacks.
 *
 * This context is created once the special attack has already landed on the target. It exposes the attacking
 * player, the victim that was struck, and the resolved [CombatDamage] that was applied on arrival.
 *
 * @property attacker The player who performed the special attack.
 * @property victim The mob hit by the special attack.
 * @property damage The resolved damage result delivered when the hit arrived.
 * @author lare96
 */
class SpecialAttackArrivedReceiver(
    val attacker: Player,
    val victim: Mob,
    val damage: CombatDamage
)