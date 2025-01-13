package world.player.skill.magic

import api.predef.*
import io.luna.game.model.mob.Player

/**
 * An enum representing all staves that can represent [Rune] types.
 */
enum class Staff(val ids: Set<Int>, val represents: Set<Rune>) {
    WATER(ids = setOf(1383, 1395, 1403),
          represents = setOf(Rune.WATER)),
    AIR(ids = setOf(1381, 1397, 1405),
        represents = setOf(Rune.AIR)),
    EARTH(ids = setOf(1385, 1399, 1407),
          represents = setOf(Rune.EARTH)),
    FIRE(ids = setOf(1387, 1401, 1393),
         represents = setOf(Rune.FIRE)),
    LAVA(ids = setOf(3053, 3054),
         represents = setOf(Rune.FIRE, Rune.EARTH));

    companion object {

        /**
         * An immutable mapping of all staff ids to their instances.
         */
        val ID_TO_STAFF: Map<Int, Staff> = values().run {
            val map = HashMap<Int, Staff>()
            for (staff in this) {
                for(id in staff.ids) {
                    map[id] = staff
                }
            }
            return@run map
        }
    }
}