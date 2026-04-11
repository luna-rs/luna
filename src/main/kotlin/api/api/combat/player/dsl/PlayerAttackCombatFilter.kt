package api.combat.player.dsl;

import io.luna.game.model.mob.Player
import io.luna.game.model.mob.combat.attack.CombatAttack

class PlayerAttackCombatFilter(val listeners: ArrayList<PlayerAttackCombatFilter>,
                               val filter: PlayerAttackCombatDataReceiver.() -> Boolean) {
    var attack: PlayerAttackCombatDataReceiver.() -> CombatAttack<Player>? = { null }
    fun then(attackFunction: PlayerAttackCombatDataReceiver.() -> CombatAttack<Player>?) {
        attack = attackFunction
        listeners += this
    }// todo documentation

}