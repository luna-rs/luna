package api.bot.webwalk.generate

import api.bot.webwalk.walk.crossing.LighthouseBridgeCrossing
import game.skill.agility.LighthouseBridge
import game.obj.entrances.LighthouseBasementDoor

/**
 * Native broken-bridge jumps join the Lighthouse shore to the mainland. Each direction clicks its own jump spot;
 * the normal landing includes the final source walking step. The basement's two source wall passages retain their
 * separate entry/exit directions. There is no minimum Agility admission level for the bridge.
 *
 * @author lare96
 */
object LighthouseBridgeSpec {

    val WEST_TO_EAST = CrossingSpec(LighthouseBridgeCrossing.ID, setOf(4615), bidirectional = false, cost = 6,
        placements = listOf(CrossingPlacement(4615, LighthouseBridge.WEST_OBJECT, LighthouseBridge.WEST,
            LighthouseBridge.EAST_EXIT)))

    val EAST_TO_WEST = CrossingSpec(LighthouseBridgeCrossing.ID, setOf(4616), bidirectional = false, cost = 6,
        placements = listOf(CrossingPlacement(4616, LighthouseBridge.EAST_OBJECT, LighthouseBridge.EAST,
            LighthouseBridge.WEST_EXIT)))

    val BASEMENT_WALLS = LighthouseBasementDoor.Wall.entries.map { wall ->
        CrossingSpec(LighthouseBridgeCrossing.ID, setOf(wall.id), bidirectional = false, cost = 4,
            placements = listOf(CrossingPlacement(wall.id, wall.position, wall.from, wall.to)))
    }

    val ALL = listOf(WEST_TO_EAST, EAST_TO_WEST) + BASEMENT_WALLS
}
