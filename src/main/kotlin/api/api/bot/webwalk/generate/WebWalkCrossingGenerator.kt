package api.bot.webwalk.generate

import api.bot.webwalk.model.EdgeType
import api.bot.webwalk.model.Requirements
import api.bot.webwalk.walk.crossing.BorderGateCrossing
import game.npc.spawn.alKharid.BorderGate
import io.luna.game.cache.map.MapIndexTable

/**
 * A kind of crossing that a [api.bot.webwalk.walk.crossing.CrossingHandler] crosses, and the facts about it that the
 * generator needs to put it in the web. These are facts about game objects, the same kind of thing as the teleport spells that
 * `teleports.jsonc` is made from, so they are written in code and not in a data file.
 *
 * @property handler The name of the handler that crosses it.
 * @property closedObjectIds The ids of the objects, in the state that the map places them in, that make a crossing.
 * @property requirements What a bot needs to cross it, from the tile of the object to the tile on the far side.
 * @property reverseRequirements What a bot needs to cross it back, or `null` for the same as [requirements].
 * @property bidirectional If it can be crossed back.
 * @property cost The time to cross it in game ticks.
 *
 * @author Hydrozoa
 */
data class CrossingSpec(val handler: String,
                        val closedObjectIds: Set<Int>,
                        val requirements: Requirements = Requirements.NONE,
                        val reverseRequirements: Requirements? = null,
                        val bidirectional: Boolean = true,
                        val cost: Int? = null)

/**
 * Every kind of crossing with a handler. To add one, add its spec here and write its handler.
 *
 * @author Hydrozoa
 */
object SpecialCrossings {

    /**
     * The toll gate between Lumbridge and Al Kharid, which costs the same to pass either way. It takes the time to walk to
     * the row of the gate, click, go through the dialogue and step through, which is a guess.
     */
    val BORDER_GATE = CrossingSpec(BorderGateCrossing.ID, setOf(BorderGate.LEFT_LEAF, BorderGate.RIGHT_LEAF),
                                   Requirements(coins = BorderGate.TOLL), cost = 8)

    /**
     * Every spec.
     */
    val ALL = listOf(BORDER_GATE)
}

/**
 * Makes `crossings.jsonc`, which holds the crossings that have a handler, from the [CrossingSpec]s and the map data of the
 * cache. This needs no running world, like [WebWalkDoorGenerator] which it uses to place the crossings, so a test can run it
 * to make sure that the committed file is still up to date.
 *
 * @author Hydrozoa
 */
object WebWalkCrossingGenerator {

    /**
     * The result of a generation.
     *
     * @property obstacles The crossings, which are not sorted.
     * @property text The text of `crossings.jsonc`.
     * @property report What was made and what was left out.
     */
    class Result(val obstacles: List<GeneratedObstacle>, val text: String, val report: GenerationReport)

    /**
     * Makes the crossings of the specs that are in the map data.
     *
     * @param table The map data.
     * @param specs The kinds of crossing. An object id may only be in one of them.
     * @return The crossings. A spec with an object that the map doesn't have is reported.
     */
    fun generate(table: MapIndexTable, specs: List<CrossingSpec> = SpecialCrossings.ALL): Result {
        val ids = specs.flatMap { it.closedObjectIds }
        require(ids.size == ids.toSet().size) { "An object is in the specs of more than one crossing: $ids" }

        val report = GenerationReport()
        val crossings = ArrayList<GeneratedObstacle>()
        for (spec in specs) {
            val (placed, placedReport) = WebWalkDoorGenerator.generate(table, spec.closedObjectIds.associateWith { EdgeType.CROSSING })
            placedReport.skipped.forEach { report.skipped += it }
            for (id in spec.closedObjectIds) {
                if (placed.none { it.objectId == id }) {
                    report.skipped += "crossing ${spec.handler}: no object $id could be placed in the map"
                }
            }
            for (obstacle in placed) {
                crossings += obstacle.copy(handler = spec.handler, cost = spec.cost, requirements = spec.requirements,
                                           reverseRequirements = spec.reverseRequirements,
                                           bidirectional = spec.bidirectional.takeIf { it != EdgeType.CROSSING.bidirectionalByDefault })
                report.count("${spec.handler} crossings")
            }
        }
        return Result(crossings, WebWalkWriter.obstacles(crossings), report)
    }
}
