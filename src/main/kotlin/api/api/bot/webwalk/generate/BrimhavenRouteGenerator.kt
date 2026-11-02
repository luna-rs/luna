package api.bot.webwalk.generate

import api.bot.webwalk.model.EdgeType
import api.bot.webwalk.model.Requirements
import api.bot.webwalk.walk.crossing.BrimhavenCrossing
import api.predef.*
import engine.obj.LadderDestination
import game.obj.entrances.BrimhavenDungeonAccess
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.World

/**
 * Generates native Brimhaven entry, exit and internal object interactions instead of linking across lava or vines.
 * Admission credit has its own free alternative; an ordinary entry reserves the actual Saniboch fee.
 * Each interior direction retains its own object half and native current-level or physical-axe requirement.
 *
 * @author lare96
 */
object BrimhavenRouteGenerator {

    /**
     * Reads real object placements on the game thread and reports unavailable stands, landings or objects.
     */
    fun gather(report: GenerationReport, gameWorld: World = world,
               canLand: (Position) -> Boolean = LadderDestination::canLand): List<GeneratedObstacle> {
        val edges = ArrayList<GeneratedObstacle>()
        fun add(id: Int, position: Position, from: Position, to: Position, requirements: Requirements, cost: Int): Boolean {
            val found = gameWorld.locator.findObjectsOnTile(position) { it.id == id }.isNotEmpty()
            if (!found || !canLand(from) || !canLand(to)) {
                report.skipped += "Brimhaven object $id at $position: missing native object, stand or landing"
                return false
            }
            edges += GeneratedObstacle(EdgeType.CROSSING, id, position, from, to,
                handler = BrimhavenCrossing.ID, cost = cost, bidirectional = false, requirements = requirements)
            return true
        }
        with(BrimhavenDungeonAccess) {
            val entranceReady = add(ENTRANCE, ENTRANCE_POSITION, OUTSIDE, INSIDE,
                Requirements(flags = setOf(BrimhavenCrossing.PREPAID)), 5)
            val saniboch = gameWorld.locator.findNpcs(OUTSIDE, 12) {
                it.id == SANIBOCH && it.state == EntityState.ACTIVE && it.isAlive
            }.firstOrNull()
            if (!entranceReady || saniboch == null) {
                report.skipped += "Brimhaven paid admission: missing Saniboch, entrance, stand or landing"
            } else {
                edges += GeneratedObstacle(EdgeType.CROSSING, null, saniboch.position, OUTSIDE, INSIDE,
                    handler = BrimhavenCrossing.ID, cost = 16, bidirectional = false, npcId = SANIBOCH,
                    requirements = Requirements(coins = FEE))
            }
            add(EXIT, EXIT_POSITION, INSIDE, OUTSIDE, Requirements.NONE, 3)
            for (passage in passages) {
                add(passage.id, passage.position, passage.from, passage.to,
                    if (passage.needsAbility) Requirements(flags = setOf(BrimhavenCrossing.flag(passage)))
                    else Requirements.NONE, if (passage.vine != null) 25 else 15)
            }
        }
        report.count("Brimhaven native crossings", edges.size)
        return edges
    }
}
