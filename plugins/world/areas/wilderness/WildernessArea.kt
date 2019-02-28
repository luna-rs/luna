package world.areas.wilderness

import api.attr.Attr
import api.predef.*
import io.luna.game.model.Area
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.inter.WalkableInterface

/**
 * An [Area] implementation for wilderness areas.
 *
 * @author lare96 <http://github.com/lare96>
 */
class WildernessArea(swX: Int, swY: Int, neX: Int, neY: Int) : Area(swX, swY, neX, neY) {

    /**
     * The "wilderness_level" attribute.
     */
    private var Player.wildernessLevel by Attr<Int>("wilderness_level")

    override fun enter(plr: Player) {
        setWildernessLevel(plr)
        plr.interfaces.open(WalkableInterface(197))
        plr.interactions.show(INTERACTION_ATTACK)
    }

    override fun exit(plr: Player) {
        plr.interactions.hide(INTERACTION_ATTACK)
        plr.interfaces.closeWalkable()
        plr.wildernessLevel = 0
    }

    override fun move(plr: Player) {
        setWildernessLevel(plr)
    }

    /**
     * Sets and displys the wilderness level for [plr].
     */
    private fun setWildernessLevel(plr: Player): Int {

        var newLevel = plr.position.y
        if (newLevel > 6400) {
            newLevel -= 6400
        }
        newLevel -= 3520
        newLevel /= 8
        newLevel++
        plr.wildernessLevel = newLevel
        plr.sendText("@yel@Level: $newLevel", 199)
        return newLevel
    }
}