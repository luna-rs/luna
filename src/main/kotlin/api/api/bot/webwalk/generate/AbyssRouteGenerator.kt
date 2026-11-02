package api.bot.webwalk.generate

import api.bot.webwalk.model.EdgeType
import api.bot.webwalk.model.Requirements
import api.bot.webwalk.walk.crossing.AbyssCrossing
import api.bot.zone.SubZone
import api.predef.*
import engine.obj.LadderDestination
import game.content.abyss.AbyssPassage
import game.content.abyss.AbyssRift
import io.luna.game.model.Position
import io.luna.game.model.World
import io.luna.game.model.`object`.GameObject

/**
 * The native mage teleport, dynamic outer-ring crossing and usable altar rifts.
 *
 * @author lare96
 */
object AbyssRouteGenerator {

    /**
     * Records the random entry and player-specific obstacle as compound crossings. Their handler performs the native
     * interactions and walks to the recorded arrivals; it never moves the player itself.
     */
    fun gather(report: GenerationReport, interactionStand: (GameObject) -> Position?,
               standableNear: (Position) -> Position?, gameWorld: World = world,
               canLand: (Position) -> Boolean = LadderDestination::canLand): List<GeneratedObstacle> {
        val crossings = ArrayList<GeneratedObstacle>()
        val allowed = Requirements(flags = setOf(AbyssCrossing.ENTRY_ALLOWED))
        val outer = standableNear(SubZone.ABYSS.inside)
        val inner = standableNear(AbyssCrossing.INNER)
        val mageFrom = standableNear(SubZone.ABYSS_ENTRY_MAGE.inside)
        val mage = gameWorld.locator.findNpcs(SubZone.ABYSS_ENTRY_MAGE.inside, 16) {
            it.id == AbyssCrossing.MAGE
        }.firstOrNull()
        if (mage != null && mageFrom != null && outer != null) {
            crossings += GeneratedObstacle(EdgeType.CROSSING, null, mage.position, mageFrom, outer,
                handler = AbyssCrossing.ID, cost = 35, bidirectional = false, npcId = AbyssCrossing.MAGE,
                requirements = allowed)
        } else report.skipped += "Abyss mage: no NPC, departure tile or outer-ring arrival"

        val passage = gameWorld.locator.findObjects(SubZone.ABYSS.inside, 64) { obj ->
            AbyssPassage.entries.any { it.id == obj.id }
        }.minByOrNull { it.id }
        if (passage != null && outer != null && inner != null) {
            crossings += GeneratedObstacle(EdgeType.CROSSING, passage.id, passage.position, outer, inner,
                handler = AbyssCrossing.ID, cost = 60, bidirectional = false, requirements = allowed)
        } else report.skipped += "Abyss outer ring: no native passage or ring arrival"

        for (rift in AbyssRift.entries) {
            val destination = rift.destination ?: continue
            val obj = gameWorld.locator.findObjects(SubZone.ABYSS.inside, 64) { it.id == rift.id }.firstOrNull()
            val from = obj?.let(interactionStand)
            if (obj == null || from == null || !canLand(destination)) {
                report.skipped += "Abyss rift ${rift.name}: no object, interaction tile or altar landing"
                continue
            }
            crossings += GeneratedObstacle(EdgeType.CROSSING, rift.id, obj.position, from, destination,
                handler = AbyssCrossing.ID, cost = 2, bidirectional = false,
                requirements = if (rift == AbyssRift.LAW)
                    Requirements(flags = setOf(AbyssCrossing.LAW_ALLOWED)) else Requirements.NONE)
        }
        report.count("Abyss crossings", crossings.size)
        return crossings
    }
}
