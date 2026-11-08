package game.skill.slayer

import api.combat.player.PlayerCombatHandler.playerStopAttack
import io.luna.game.model.mob.Npc

/**
 * Stops a player's attack on a Slayer monster when their Slayer level is too low to damage it.
 */
playerStopAttack { attack ->
    val failure = (attack.victim as? Npc)?.let { SlayerLevels.failureMessage(this, it) }
    if (failure == null) false else {
        sendMessage(failure)
        true
    }
}
