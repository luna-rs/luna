package game.skill.magic.teleOther

import io.luna.game.action.impl.LockedAction
import io.luna.game.model.Entity
import io.luna.game.model.Position
import io.luna.game.model.mob.Player

/**
 * Plays the mine portal effect and returns the player one game tick later.
 *
 * @author lare96
 */
class EssenceMineExitAction(player: Player, private val portal: Entity, private val destination: Position) :
    LockedAction(player) {

    override fun run(): Boolean {
        if (executions == 0) {
            mob.walking.clear()
            EssenceMineTeleport.effects(portal, mob, true)
            return false
        }
        mob.move(destination)
        return true
    }
}
