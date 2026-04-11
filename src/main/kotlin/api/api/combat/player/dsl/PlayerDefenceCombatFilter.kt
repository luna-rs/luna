package api.combat.player.dsl;

import io.luna.game.model.mob.Player
import io.luna.game.model.mob.combat.attack.CombatAttack

class PlayerDefenceCombatFilter(val listeners: ArrayList<PlayerDefenceCombatFilter>,
                                val filter: PlayerDefenceCombatDataReceiver.() -> Boolean) {
    var defence: PlayerDefenceCombatDataReceiver.() -> Unit = { }
    fun then(defenceFunction: PlayerDefenceCombatDataReceiver.() -> Unit) {
        defence = defenceFunction
        listeners += this
    }// todo documentation

}