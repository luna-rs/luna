package api.bot.webwalk.generate

import api.bot.webwalk.model.EdgeType
import api.bot.webwalk.walk.crossing.KillerwattCrossing
import api.predef.world
import engine.obj.LadderDestination
import game.obj.entrances.KillerwattAccess
import io.luna.game.model.Position
import io.luna.game.model.World

/**
 * Connects the actual laboratory rift and return portal through their native warning interaction.
 * These entrances are scripted separately from the ordinary entrance catalogue.
 *
 * @author lare96
 */
object KillerwattRouteGenerator {

    /**
     * Adds each direction only when its native object and both legal landing tiles exist in the loaded world.
     */
    fun gather(report: GenerationReport, gameWorld: World = world,
               canLand: (Position) -> Boolean = LadderDestination::canLand): List<GeneratedObstacle> {
        val routes = listOf(
            Triple(KillerwattAccess.RIFT, KillerwattAccess.RIFT_POSITION,
                KillerwattAccess.LAB to KillerwattAccess.REALM),
            Triple(KillerwattAccess.PORTAL, KillerwattAccess.PORTAL_POSITION,
                KillerwattAccess.REALM to KillerwattAccess.LAB))
        return routes.mapNotNull { (id, objectPosition, ends) ->
            if (!canLand(ends.first) || !canLand(ends.second) ||
                gameWorld.objects.findAll(objectPosition).noneMatch { it.id == id }) {
                report.skipped += "Killerwatt crossing $id: missing object or legal landing"
                null
            } else {
                report.count("Killerwatt portal crossings")
                GeneratedObstacle(EdgeType.CROSSING, id, objectPosition, ends.first, ends.second,
                    handler = KillerwattCrossing.ID, bidirectional = false, cost = 20)
            }
        }
    }
}
