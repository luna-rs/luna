package game.bot.scripts.skills

import api.bot.zone.SubZone
import api.predef.ctx
import game.skill.mining.Ore
import io.luna.game.cache.map.MapIndexTable
import io.luna.game.cache.map.MapObject
import java.util.EnumMap

/**
 * Known mining geography from the decoded cache. These placements identify places to inspect, not whether a
 * live rock currently has ore. The game-thread catalog is rebuilt if the decoded map is replaced.
 *
 * @author TheLining
 */
internal object MiningSites {
    data class Rock(val placement: MapObject, val ore: Ore) {
        val position get() = placement.position
    }

    private var source: MapIndexTable? = null
    private var rocks: List<Rock> = emptyList()
    private val zones = EnumMap<SubZone, List<Rock>>(SubZone::class.java)

    fun inZone(zone: SubZone): List<Rock> {
        val map = ctx.cache.mapIndexTable ?: return emptyList()
        if (source !== map) {
            rocks = map.objectSet.objects.mapNotNull { placement ->
                Ore.ROCK_MAP[placement.objectId]?.let { Rock(placement, it) }
            }
            zones.clear()
            source = map
        }
        // The Dwarven Mine box overlaps the separately gated guild chamber.
        return zones.getOrPut(zone) {
            rocks.filter {
                zone.area.contains(it.position) &&
                    (zone == SubZone.MINING_GUILD || !SubZone.MINING_GUILD.area.contains(it.position))
            }
        }
    }
}
