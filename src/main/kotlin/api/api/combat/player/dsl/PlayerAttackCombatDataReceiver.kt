package api.combat.player.dsl

import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.combat.attack.CombatAttack

class PlayerAttackCombatDataReceiver(player: Player, other: Mob) : PlayerCombatDataReceiver(player, other) {
    fun default(): CombatAttack<Player> = player.combat.getDefaultAttack(other)
}// todo documentation
