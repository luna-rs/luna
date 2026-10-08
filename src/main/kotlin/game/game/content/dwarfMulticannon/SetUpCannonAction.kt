package game.content.dwarfMulticannon

import api.predef.*
import game.content.dwarfMulticannon.DwarfMulticannon.cannonPosition
import game.content.dwarfMulticannon.DwarfMulticannon.cannonStage
import io.luna.game.action.impl.LockedAction
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.path.Pathfinders

/**
 * A [LockedAction] that steps the player out of the way and places a cannon base around [centre].
 *
 * @author TheLining
 */
class SetUpCannonAction(plr: Player, private val centre: Position, private val step: Position) :
    LockedAction(plr, true, 1) {

    companion object {

        /**
         * The animation for placing a part.
         */
        val PLACE_ANIMATION = Animation(827)
    }

    /**
     * If the player has reached [step] and turned to face [centre].
     */
    private var facing = false

    override fun run(): Boolean {
        if (executions == 0) {
            val path = mob.navigator.findPath(mob.position, step, Pathfinders.forPlayer(mob), false).join()
            mob.walking.replacePath(path)
            return false
        }
        if (!facing) {
            if (mob.walking.isEmpty) {
                mob.face(centre)
                facing = true
            }
            return false
        }

        val origin = centre.translate(-1, -1)
        if (DwarfMulticannon.isOccupied(origin)) {
            mob.sendMessage("There isn't enough space to set up here.")
        } else if (mob.inventory.remove(Item(CannonStage.BASE.partId))) {
            mob.cannonPosition = origin
            mob.cannonStage = CannonStage.BASE
            mob.animation(PLACE_ANIMATION)
            val cannon = Cannon(mob.username, origin)
            Cannon.ALL[mob.username] = cannon
            world.schedule(cannon)
            mob.sendMessage("You place the cannon base on the ground.")
        }
        return true
    }
}
