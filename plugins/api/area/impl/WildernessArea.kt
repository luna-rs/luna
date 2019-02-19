package api.area.impl

import api.area.Area
import io.luna.game.model.mob.Player

/**
 *
 */
class WildernessArea(swX: Int, swY: Int,
                     neX: Int, neY: Int,
                     z: IntRange = 0..4) : Area(swX, swY, neX, neY, z) {

    override fun enter(plr: Player) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun exit(plr: Player) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}