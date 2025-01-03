package world.player.skill.magic.telekineticGrab

import io.luna.game.action.Action
import io.luna.game.action.RepeatingAction
import io.luna.game.model.item.GroundItem
import io.luna.game.model.mob.Player

/**
 * A
 */
class TelekineticGrabAction(plr: Player, val groundItem: GroundItem) : RepeatingAction<Player>(plr, false, 1) {
   //todo everything
    override fun start(): Boolean {
        TODO("Not yet implemented")
    }

    override fun repeat() {
        TODO("Not yet implemented")
    }

    override fun ignoreIf(other: Action<*>?): Boolean {
        TODO("Not yet implemented")
    }
}