package api.combat.player

import api.combat.player.dsl.PlayerAttackCombatDataReceiver
import api.combat.player.dsl.PlayerAttackCombatFilter
import api.combat.player.dsl.PlayerDefenceCombatDataReceiver
import api.combat.player.dsl.PlayerDefenceCombatFilter
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.combat.attack.CombatAttack
import io.luna.game.model.mob.combat.damage.CombatDamageAction

object PlayerCombatHandler {
    // todo documentation
    val attackListeners = ArrayList<PlayerAttackCombatFilter>()
    val defenceListeners = ArrayList<PlayerDefenceCombatFilter>()

    fun playerAttack(filter: PlayerAttackCombatDataReceiver.() -> Boolean): PlayerAttackCombatFilter {
        return PlayerAttackCombatFilter(attackListeners, filter)
    }

    fun playerDefence(filter: PlayerDefenceCombatDataReceiver.() -> Boolean): PlayerDefenceCombatFilter {
        return PlayerDefenceCombatFilter(defenceListeners, filter)
    }

    fun supplyAttack(attacker: Player, victim: Mob): CombatAttack<Player>? {
        for (hook in attackListeners) {
            val receiver = PlayerAttackCombatDataReceiver(attacker, victim)
            if (hook.filter(receiver)) {
                // We matched on this hook, instead of null, do nothing.
                val attack = hook.attack(receiver)
                return attack ?: VoidCombatAttack(attacker, victim)
            }
        }
        // No hooks matched, return to normal attack selection.
        return null
    }

    fun consumeDefence(player: Player, other: Mob, action: CombatDamageAction) {
        for (hook in defenceListeners) {
            val receiver = PlayerDefenceCombatDataReceiver(player, other, action)
            if (hook.filter(receiver)) {
                hook.defence(receiver)
                action.damage = receiver.damage
                player.animation(Animation(receiver.animationId))
                return
            }
        }
    }
}