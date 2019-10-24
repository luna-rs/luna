package world.areas.wilderness

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
    // TODO Scrap this concept for "controllers" that are activated when you walk to <X> area
    // Also general purpose controllers that can be instantiated programmatically
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
        var newLevel = if (plr.position.y > 6400) plr.position.y - 6400 else plr.position.y
        newLevel = ((newLevel - 3520) / 8) + 1
        plr.wildernessLevel = newLevel
        plr.sendText("@yel@Level: $newLevel", 199)
        return newLevel
    }
}