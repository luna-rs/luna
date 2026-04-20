package api.combat.player.dsl

import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.combat.attack.CombatAttack
import io.luna.game.model.mob.combat.damage.CombatDamage
import io.luna.game.model.mob.combat.damage.CombatDamageAction

class PlayerDefenceCombatDataReceiver(player: Player, other: Mob, action: CombatDamageAction) :
    PlayerCombatDataReceiver(player, other) {

    var animationId: Int = player.combat.defenceAnimation


    var damage: CombatDamage? = action.damage


    val source: CombatAttack<*> = action.source
}// todo documentation
