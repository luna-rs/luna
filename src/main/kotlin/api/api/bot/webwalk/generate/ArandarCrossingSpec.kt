package api.bot.webwalk.generate

import api.bot.webwalk.model.Requirements
import api.bot.webwalk.plan.BotConditions
import api.bot.webwalk.walk.crossing.AgilityShortcutCrossing
import api.bot.webwalk.walk.crossing.ArandarCrossing
import api.bot.webwalk.walk.crossing.IsafdarLogCrossing
import game.obj.doors.ArandarGate
import game.skill.agility.IsafdarForest
import game.skill.agility.IsafdarLogs
import game.skill.agility.IsafdarTripwire
import io.luna.game.model.Position
import io.luna.game.model.`object`.ObjectDirection

/**
 * The Arandar palisade and native woodland passages through Isafdar to Iorwerth's camp.
 *
 * @author lare96
 */
object ArandarCrossingSpec {

    val TRIPWIRES = CrossingSpec(AgilityShortcutCrossing.ID, setOf(IsafdarTripwire.ID), cost = 8,
        placements = listOf(
            tripwire(2215, 3154, ObjectDirection.EAST),
            tripwire(2220, 3153, ObjectDirection.WEST),
            tripwire(2251, 3168, ObjectDirection.SOUTH),
            tripwire(2285, 3188, ObjectDirection.SOUTH),
            tripwire(2294, 3243, ObjectDirection.WEST)))

    val LOGS = CrossingSpec(IsafdarLogCrossing.ID, IsafdarLogs.ENDS.keys,
        Requirements(flags = setOf(BotConditions.ISAFDAR_LOG_ENTRY)), cost = 9,
        placements = IsafdarLogs.ENDS.map { (id, ends) ->
            val position = ends.first()
            val (from, to) = IsafdarLogs.sides(id, position)
            CrossingPlacement(id, position, from, to)
        })

    val GATE = CrossingSpec(ArandarCrossing.ID, setOf(ArandarGate.LEFT), cost = 6,
        placements = listOf(CrossingPlacement(ArandarGate.LEFT, ArandarGate.LEFT_POSITION,
            ArandarGate.NORTH, ArandarGate.SOUTH)))

    val FOREST = CrossingSpec(AgilityShortcutCrossing.ID, IsafdarForest.IDS,
        Requirements(flags = setOf(BotConditions.ISAFDAR_FOREST_ENTRY)), cost = 4,
        placements = listOf(
            forest(3939, 2165, 3189, ObjectDirection.SOUTH),
            forest(3939, 2173, 3156, ObjectDirection.EAST),
            forest(3939, 2187, 3166, ObjectDirection.EAST),
            forest(3939, 2216, 3161, ObjectDirection.EAST),
            forest(3938, 2216, 3164, ObjectDirection.EAST),
            forest(3939, 2216, 3167, ObjectDirection.EAST),
            forest(3937, 2232, 3148, ObjectDirection.SOUTH),
            forest(3938, 2235, 3148, ObjectDirection.SOUTH),
            forest(3939, 2238, 3148, ObjectDirection.SOUTH),
            forest(3938, 2227, 3218, ObjectDirection.NORTH),
            forest(3939, 2230, 3218, ObjectDirection.NORTH),
            forest(3938, 2231, 3248, ObjectDirection.SOUTH),
            forest(3937, 2233, 3218, ObjectDirection.NORTH),
            forest(3939, 2234, 3248, ObjectDirection.SOUTH),
            forest(3939, 2236, 3218, ObjectDirection.NORTH),
            forest(3937, 2237, 3248, ObjectDirection.SOUTH),
            forest(3938, 2266, 3191, ObjectDirection.NORTH),
            forest(3939, 2269, 3191, ObjectDirection.NORTH),
            forest(3937, 2272, 3191, ObjectDirection.NORTH),
            forest(3938, 2278, 3223, ObjectDirection.EAST),
            forest(3937, 2278, 3226, ObjectDirection.EAST),
            forest(3939, 2278, 3229, ObjectDirection.WEST),
            forest(3937, 2302, 3214, ObjectDirection.WEST),
            forest(3938, 2302, 3217, ObjectDirection.EAST),
            forest(3939, 2302, 3220, ObjectDirection.WEST),
            forest(3938, 2302, 3223, ObjectDirection.EAST)))

    /**
     * Uses the native rotated geometry for a cache placement.
     */
    private fun forest(id: Int, x: Int, y: Int, direction: ObjectDirection): CrossingPlacement {
        val position = Position(x, y)
        val (from, to) = IsafdarForest.sides(position, direction)
        return CrossingPlacement(id, position, from, to)
    }

    /**
     * Uses the native step-over geometry for a surface tripwire placement.
     */
    private fun tripwire(x: Int, y: Int, direction: ObjectDirection): CrossingPlacement {
        val position = Position(x, y)
        val (from, to) = IsafdarTripwire.sides(position, direction)
        return CrossingPlacement(IsafdarTripwire.ID, position, from, to)
    }
}
