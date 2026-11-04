package api.bot.webwalk.generate

import api.bot.webwalk.model.Requirements
import api.bot.webwalk.walk.crossing.YanilleDungeonCrossing
import game.skill.agility.yanille.YanilleDungeon
import io.luna.game.model.Position

/**
 * Directed native obstacle entrances joining the dungeon's otherwise disconnected chambers.
 * Separate placements click the object at the actual end being used, including the free rubble descent.
 *
 * @author lare96
 */
object YanilleDungeonCrossingSpec {

    private fun obstacle(id: Int, level: Int, cost: Int, placements: List<CrossingPlacement>) =
        CrossingSpec(YanilleDungeonCrossing.ID, setOf(id),
            requirements = if (level == 0) Requirements.NONE else
                Requirements(flags = setOf(YanilleDungeonCrossing.flag(level))),
            bidirectional = false, cost = cost, placements = placements)

    val ALL = with(YanilleDungeon) {
        listOf(
            obstacle(LEDGE, 40, 8, listOf(
                CrossingPlacement(LEDGE, Position(2580, 9519), LEDGE_NORTH, LEDGE_SOUTH),
                CrossingPlacement(LEDGE, Position(2580, 9513), LEDGE_SOUTH, LEDGE_NORTH))),
            obstacle(PIPE, 49, 6, listOf(
                CrossingPlacement(PIPE, Position(2576, 9506), PIPE_EAST, PIPE_WEST),
                CrossingPlacement(PIPE, Position(2573, 9506), PIPE_WEST, PIPE_EAST))),
            obstacle(MONKEY_BARS, 57, 12, listOf(
                CrossingPlacement(MONKEY_BARS, Position(2598, 9494), Position(2598, BARS_NORTH),
                    Position(2598, BARS_SOUTH)),
                CrossingPlacement(MONKEY_BARS, Position(2598, 9489), Position(2598, BARS_SOUTH),
                    Position(2598, BARS_NORTH)))),
            obstacle(RUBBLE_UP, 67, 2, listOf(
                CrossingPlacement(RUBBLE_UP, Position(2616, 9572), RUBBLE_BOTTOM, RUBBLE_TOP))),
            obstacle(RUBBLE_DOWN, 0, 2, listOf(
                CrossingPlacement(RUBBLE_DOWN, Position(2615, 9504), RUBBLE_TOP, RUBBLE_BOTTOM))))
    }
}
