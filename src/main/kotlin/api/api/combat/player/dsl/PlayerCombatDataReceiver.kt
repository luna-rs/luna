package api.combat.player.dsl

import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.combat.state.PlayerCombatContext

open class PlayerCombatDataReceiver(val player: Player, val other: Mob) {
    val combat: PlayerCombatContext = player.combat
}// todo documentation
