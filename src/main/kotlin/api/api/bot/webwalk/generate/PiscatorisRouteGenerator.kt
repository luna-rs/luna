package api.bot.webwalk.generate

import api.bot.webwalk.model.EdgeType
import api.bot.webwalk.model.Requirements
import api.bot.webwalk.walk.crossing.PiscatorisCrossing
import api.predef.*
import engine.obj.LadderDestination
import game.content.piscatoris.PiscatorisFerry
import io.luna.game.model.Position
import io.luna.game.model.World

/**
 * Generates Kathy's two native NPC boat crossings from the configured shore crews.
 *
 * @author lare96
 */
object PiscatorisRouteGenerator {

    /**
     * Reads the real ferrymen on the game thread and retains the fare for planning and bank withdrawals.
     */
    fun gather(report: GenerationReport, standableNear: (Position) -> Position?,
               gameWorld: World = world, canLand: (Position) -> Boolean = LadderDestination::canLand): List<GeneratedObstacle> =
        PiscatorisFerry.Route.entries.mapNotNull { route ->
            val npc = gameWorld.locator.findNpcs(route.departure, 10) { PiscatorisFerry.route(it) == route }.firstOrNull()
            val from = standableNear(route.departure)
            if (npc == null || from == null || !canLand(route.destination)) {
                report.skipped += "Piscatoris ferry ${route.place}: missing crew or shore landing"
                null
            } else {
                report.count("Piscatoris ferry crossings")
                GeneratedObstacle(EdgeType.CROSSING, null, npc.position, from, route.destination,
                    handler = PiscatorisCrossing.ID, cost = 12, bidirectional = false, npcId = npc.id,
                    requirements = Requirements(coins = PiscatorisFerry.FARE))
            }
        }
}
