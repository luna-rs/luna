package game.content.fightPit

import game.skill.magic.teleportSpells.TeleportAction
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.controller.PlayerController

/**
 * Looks after a player in the Fight Pit's waiting room or in the pit.
 *
 * @author TheLining
 */
class FightPitController(private val plr: Player) : PlayerController(plr) {

    override fun register() {
        FightPit.showOverlay(plr)
    }

    override fun unregister() {
        FightPit.hideOverlay(plr)
    }

    override fun move() {
        if (!FightPit.inside(plr.position)) plr.controllers.unregister()
    }

    // A player who logs out anywhere in the Fight Pit comes back outside the north door.
    override fun getPersistencePosition(): Position = FightPit.OUTSIDE

    override fun teleport(action: TeleportAction): Boolean {
        if (FightPit.PIT.contains(plr.position)) {
            plr.sendMessage("You can't teleport out of the Fight Pit.")
            return false
        }
        return true
    }
}
