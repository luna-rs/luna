package api.combat.player.dsl

import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.combat.attack.CombatAttack
import io.luna.game.model.mob.combat.damage.CombatDamage
import io.luna.game.model.mob.combat.damage.CombatDamageAction

/**
 * A combat data receiver used when a player is defending against incoming combat damage.
 *
 * This receiver exposes the incoming damage action data to combat scripts, allowing scripts to inspect or override
 * the damage, defence animation, and attack source before the hit is fully processed.
 *
 * @property damage The incoming damage that will be applied to the defending player.
 * @property animationId The optional defence animation id to perform when receiving the hit.
 * @property source The combat attack source that produced the incoming damage action.
 * @author lare96
 */
class PlayerDefenceCombatDataReceiver(player: Player, other: Mob, action: CombatDamageAction) :
    PlayerCombatDataReceiver(player, other) {

    /**
     * The incoming damage that will be applied to the defending player.
     *
     * This value may be changed by combat scripts to reduce, increase, replace, or cancel the received damage.
     */
    var damage: CombatDamage? = action.damage

    /**
     * The optional defence animation id to perform when the player receives the hit.
     *
     * A `null` value means no override has been supplied by the script.
     */
    var animationId: Int? = null

    /**
     * The combat attack source that produced this incoming damage.
     */
    val source: CombatAttack<*> = action.source
}