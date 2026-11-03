package api.bot.webwalk.generate

import api.bot.webwalk.model.EdgeType
import api.bot.webwalk.walk.crossing.NativeVoyageCrossing
import api.predef.*
import engine.obj.LadderDestination
import game.content.nativeTravel.NativeVoyages
import io.luna.game.model.Position
import io.luna.game.model.World
import io.luna.game.model.Direction
import io.luna.game.model.mob.interact.InteractionPolicy

/**
 * Generates each free native NPC voyage as its own directed Talk-to crossing.
 * The same content route supplies the crew, destination and sailing duration used by players.
 *
 * @author lare96
 */
object NativeVoyageRouteGenerator {

    /**
     * Includes the crew conversation in the estimated cost and verifies both physical shore stands.
     */
    fun gather(report: GenerationReport, standableNear: (Position) -> Position?, gameWorld: World = world,
               canLand: (Position) -> Boolean = LadderDestination::canLand): List<GeneratedObstacle> =
        NativeVoyages.Route.entries.mapNotNull { route ->
            val npc = gameWorld.locator.findNpcs(route.departure, 10) { NativeVoyages.route(it) == route }.firstOrNull()
            val from = if (npc == null) null else {
                (listOf(route.departure, npc.position) + Direction.NESW.map { npc.position.translate(1, it) })
                    .mapNotNull(standableNear).distinct().firstOrNull {
                        canLand(it) && gameWorld.collisionManager.reached(it, npc, InteractionPolicy.STANDARD_SIZE)
                    }
            }
            if (npc == null || from == null || !canLand(from) || !canLand(route.destination)) {
                report.skipped += "Native voyage ${route.name}: missing crew or legal shore landing"
                null
            } else {
                report.count("native voyage crossings")
                GeneratedObstacle(EdgeType.CROSSING, null, npc.position, from, route.destination,
                    option = "Talk-to", handler = NativeVoyageCrossing.ID, cost = route.ticks + 6,
                    bidirectional = false, npcId = route.npcId)
            }
        }
}
