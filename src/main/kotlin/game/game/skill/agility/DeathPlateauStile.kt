package game.skill.agility

import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection
import io.luna.game.model.`object`.ObjectType

/**
 * The native stile behind Tenzing's hut, sharing the source's ordinary walk-style crossing without admission or XP.
 *
 * @author lare96
 */
object DeathPlateauStile {

    const val ID = 3730
    val POSITION = Position(2817, 3562)
    val SOUTH = Position(2817, 3561)
    val NORTH = Position(2817, 3564)

    /**
     * Accepts only the actual revision377 stile before using the ordinary two-tile crossing sequence.
     */
    fun action(player: Player, obj: GameObject): AgilityAction? {
        if (obj.id != ID || obj.position != POSITION || obj.direction != ObjectDirection.WEST ||
            obj.objectType != ObjectType.DEFAULT || obj.state != EntityState.ACTIVE || obj.isHidden ||
            !obj.isVisibleTo(player) || player.state != EntityState.ACTIVE || !player.isAlive) return null
        return Stile.action(player, obj)
    }
}
