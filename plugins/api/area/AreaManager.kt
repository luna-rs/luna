package api.area

import api.area.impl.WildernessArea
import com.google.common.collect.ImmutableSet
import io.luna.game.model.mob.Player

object AreaManager {

   private val areas: MutableSet<Area> = HashSet()

    init {
        areas += WildernessArea(swX = 2941,
                swY = 3518,
                neX = 3392,
                neY = 3966,
                z = 0..3)
    }

    fun register(area: Area): Boolean {
        requireNotNull(area)
       return areas.add(area)
    }

    fun updateLocation(plr: Player) {

    }

    fun all(): Set<Area> = areas
}