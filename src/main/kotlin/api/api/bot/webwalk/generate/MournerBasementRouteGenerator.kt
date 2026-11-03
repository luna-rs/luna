package api.bot.webwalk.generate

import api.bot.webwalk.model.EdgeType
import api.bot.webwalk.walk.crossing.NativeAccessCrossing
import api.predef.*
import engine.obj.LadderDestination
import game.obj.entrances.MournerBasement
import io.luna.game.model.Position
import io.luna.game.model.World
import io.luna.game.model.`object`.GameObject

/**
 * Connects the Mourner basement's native swinging hatch to its fixed landing.
 * The existing special ladder supplies the independent return route.
 *
 * @author lare96
 */
object MournerBasementRouteGenerator {

    /**
     * Uses a collision-validated interaction stand and the shared native hatch definition.
     */
    fun gather(report: GenerationReport, gameWorld: World = world,
               standingTiles: (GameObject) -> List<Position> = { LadderDestination.climbingTiles(it).toList() },
               canLand: (Position) -> Boolean = LadderDestination::canLand): List<GeneratedObstacle> {
        val obj = gameWorld.objects.staticIterator().asSequence().firstOrNull {
            it.id == MournerBasement.CLOSED && it.position == MournerBasement.POSITION
        } ?: return emptyList()
        val from = standingTiles(obj).filter(canLand).minWithOrNull(compareBy({ it.z }, { it.x }, { it.y }))
        if (from == null || !canLand(MournerBasement.LANDING)) {
            report.skipped += "Mourner basement hatch: no legal native interaction stand or landing"
            return emptyList()
        }
        return listOf(GeneratedObstacle(EdgeType.CROSSING, obj.id, obj.position, from, MournerBasement.LANDING,
            option = "Open", handler = NativeAccessCrossing.ID, cost = 4, bidirectional = false))
    }
}
