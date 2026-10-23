package api.bot.webwalk.generate

import api.bot.webwalk.model.EdgeType
import api.bot.webwalk.model.Requirements
import api.bot.webwalk.walk.crossing.BorderGateCrossing
import api.bot.webwalk.walk.crossing.HamTrapdoorCrossing
import engine.obj.HamTrapdoor
import game.npc.spawn.alKharid.BorderGate
import io.luna.game.cache.map.MapIndexTable
import io.luna.game.model.Position

/**
 * Where a crossing is, for one whose tiles are given rather than found from a wall, such as a trapdoor that leads into a
 * dungeon.
 *
 * @property objectId The id of the object, in the state that the map places it in.
 * @property pos The position of the object.
 * @property from The tile to cross it from.
 * @property to The tile that crossing it arrives at.
 *
 * @author Hydrozoa
 */
data class CrossingPlacement(val objectId: Int, val pos: Position, val from: Position, val to: Position)

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
 * @property placements Where the crossing is, for one that isn't a straight wall. Empty to place the [closedObjectIds] as
 * walls, the way doors are placed.
 *
 * @author Hydrozoa
 */
data class CrossingSpec(val handler: String,
                        val closedObjectIds: Set<Int>,
                        val requirements: Requirements = Requirements.NONE,
                        val reverseRequirements: Requirements? = null,
                        val bidirectional: Boolean = true,
                        val cost: Int? = null,
                        val placements: List<CrossingPlacement> = emptyList())

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
     * The H.A.M. Hideout trapdoor west of Lumbridge, which is picked open and climbed down. It only goes one way, as the way
     * out is the ladder. It takes the time to pick the lock a few times and climb down, which is a guess: a bot with low
     * Thieving takes longer, but there is no other way in to compare it with.
     */
    val HAM_TRAPDOOR = CrossingSpec(HamTrapdoorCrossing.ID, setOf(HamTrapdoor.ID), cost = 20, bidirectional = false,
                                    placements = listOf(CrossingPlacement(HamTrapdoor.ID, HamTrapdoor.POSITION,
                                                                          HamTrapdoor.USE_FROM, HamTrapdoor.HIDEOUT)))

    /**
     * Every spec.
     */
    val ALL = listOf(BORDER_GATE, HAM_TRAPDOOR)
}

/**
 * Makes `crossings.jsonc`, which holds the crossings that have a handler, from the [CrossingSpec]s and the map data of the
 * cache. This needs no running world, like [WebWalkDoorGenerator] which it uses to place the crossings that are walls, so a
 * test can run it to make sure that the committed file is still up to date. A crossing with [CrossingSpec.placements] is put
 * at the tiles they give, as long as the map has its object there.
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
            val (placed, placedReport) = if (spec.placements.isEmpty())
                WebWalkDoorGenerator.generate(table, spec.closedObjectIds.associateWith { EdgeType.CROSSING })
            else place(table, spec.placements)
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

    /**
     * Places crossings at the tiles that they are given, if the map data has their objects where they say.
     *
     * @param table The map data.
     * @param placements Where the crossings are.
     * @return The crossings whose objects the map has, and the ones that were left out.
     */
    private fun place(table: MapIndexTable, placements: List<CrossingPlacement>): Pair<List<GeneratedObstacle>, GenerationReport> {
        val report = GenerationReport()
        val placed = ArrayList<GeneratedObstacle>()
        for (placement in placements) {
            if (table.objectSet.none { it.objectId == placement.objectId && it.position == placement.pos }) {
                report.skipped += "CROSSING ${placement.objectId} at ${DoorObstacles.describe(placement.pos)}: not in the map"
                continue
            }
            placed += GeneratedObstacle(EdgeType.CROSSING, placement.objectId, placement.pos, placement.from, placement.to)
        }
        return Pair(placed, report)
    }
}
