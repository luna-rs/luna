package game.bot.scripts.skills

import game.skill.agility.AgilityObstacle.*
import game.skill.agility.yanille.YanilleDungeon
import io.luna.game.model.Position

/**
 * Exact native room crossings for independent Yanille training visits, without lap credit or rewards.
 * Each visit returns through the opposite native option before repeating the same obstacle.
 *
 * @author lare96
 */
internal object YanilleAgilityTraining {
    /**
     * West spider-cave floor reached by ledge and bar failures, distinct from the eastern rubble cave.
     */
    fun inSpiderCave(position: Position): Boolean = position.z == 0 &&
        position.x in 2558..2607 && position.y in 9548..9590

    fun ledge(): List<AgilityStep> = listOf(
        crossing(YANILLE_LEDGE, YanilleDungeon.LEDGE, Position(2580, 9519),
            YanilleDungeon.LEDGE_NORTH, YanilleDungeon.LEDGE_SOUTH),
        crossing(YANILLE_LEDGE, YanilleDungeon.LEDGE, Position(2580, 9513),
            YanilleDungeon.LEDGE_SOUTH, YanilleDungeon.LEDGE_NORTH))

    fun pipe(): List<AgilityStep> = listOf(
        crossing(YANILLE_PIPE, YanilleDungeon.PIPE, Position(2576, 9506),
            YanilleDungeon.PIPE_EAST, YanilleDungeon.PIPE_WEST),
        crossing(YANILLE_PIPE, YanilleDungeon.PIPE, Position(2573, 9506),
            YanilleDungeon.PIPE_WEST, YanilleDungeon.PIPE_EAST))

    fun bars(): List<AgilityStep> = listOf(
        crossing(YANILLE_MONKEY_BARS, YanilleDungeon.MONKEY_BARS, Position(2598, 9489),
            Position(2598, YanilleDungeon.BARS_SOUTH), Position(2598, YanilleDungeon.BARS_NORTH - 1)),
        crossing(YANILLE_MONKEY_BARS, YanilleDungeon.MONKEY_BARS, Position(2598, 9494),
            Position(2598, YanilleDungeon.BARS_NORTH), Position(2598, YanilleDungeon.BARS_SOUTH + 1)))

    fun rubble(): List<AgilityStep> = listOf(
        crossing(YANILLE_RUBBLE_UP, YanilleDungeon.RUBBLE_UP, Position(2616, 9572),
            YanilleDungeon.RUBBLE_BOTTOM, YanilleDungeon.RUBBLE_TOP),
        crossing(YANILLE_RUBBLE_DOWN, YanilleDungeon.RUBBLE_DOWN, Position(2615, 9504),
            YanilleDungeon.RUBBLE_TOP, YanilleDungeon.RUBBLE_BOTTOM))

    private fun crossing(obstacle: game.skill.agility.AgilityObstacle, id: Int, objectTile: Position,
                         entry: Position, exit: Position): AgilityStep = AgilityStep(obstacle, 0, setOf(id),
        approach = { entry }, matches = { it.position == objectTile }, destination = { _, _ -> exit },
        searchCenter = objectTile)
}
