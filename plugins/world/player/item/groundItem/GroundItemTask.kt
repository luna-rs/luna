package world.player.item.groundItem

import api.predef.*
import io.luna.game.model.item.GroundItem
import io.luna.game.task.Task

/**
 *
 *
 * @author lare96 <http://github.com/lare96>
 */
class GroundItemTask : Task(100) {

    companion object {
        const val TRADEABLE_LOCAL_MINUTES = 1
        const val UNTRADEABLE_LOCAL_MINUTES = 3
        const val GLOBAL_MINUTES = 3
    }

    override fun execute() {
        for (item in world.items) {
            if (!item.isExpire) {
                continue
            }

            val tradeable = item.def().isTradeable
            val expireMins = item.expireMinutes
            when {
                expireMins >= TRADEABLE_LOCAL_MINUTES && tradeable -> toGlobal(item)
                expireMins >= UNTRADEABLE_LOCAL_MINUTES && !tradeable -> expire(item)
                expireMins >= GLOBAL_MINUTES && item.isGlobal -> expire(item)
            }
        }
    }

    private fun toGlobal(item: GroundItem) {

    }

    private fun expire(item: GroundItem) {

    }

}