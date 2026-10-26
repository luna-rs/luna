package game.content.pestControl

import game.skill.magic.teleportSpells.TeleportAction
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.controller.PlayerController

/**
 * Keeps safe death and participation attached to passengers until their exit or respawn completes.
 *
 * @author lare96
 */
class PestControlController(private val player: Player) : PlayerController(player) {
    override fun combat(victim: Mob): Boolean = PestControl.canAttack(player, victim)

    override fun teleport(action: TeleportAction): Boolean {
        player.sendMessage("Speak to the Squire or cross the gangplank to leave Pest Control.")
        return false
    }

    override fun logout(): Boolean {
        PestControl.leave(player)
        return true
    }

    override fun unregister() { PestControl.detach(player) }

    override fun move() {
        if (!PestControl.onIsland(player.position) && !PestControl.onLander(player.position)) {
            PestControl.leave(player)
        }
    }
}
