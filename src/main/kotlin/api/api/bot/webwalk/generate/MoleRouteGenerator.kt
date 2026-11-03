package api.bot.webwalk.generate

import api.bot.webwalk.model.EdgeType
import api.bot.webwalk.model.ItemRequirement
import api.bot.webwalk.model.Requirements
import api.bot.webwalk.walk.crossing.MoleCrossing
import engine.obj.LadderDestination
import game.item.teleport.SpadeDigSpot
import io.luna.game.model.Position

/**
 * Native spade-item entries from Falador Park's six legal dig tiles. The ordinary lair-rope entrance handler
 * generates the return independently; no fake mound object or inverse dig teleport is added.
 *
 * @author lare96
 */
object MoleRouteGenerator {
    /**
     * Validates real walking starts and the native landing before exporting inventory-item crossing metadata.
     */
    fun gather(report: GenerationReport, canLand: (Position) -> Boolean = LadderDestination::canLand): List<GeneratedObstacle> {
        val spot = SpadeDigSpot.FALADOR_MOLE_HILLS
        return spot.entryPositions.mapNotNull { from ->
            if (!canLand(from) || !canLand(spot.landing)) {
                report.skipped += "Giant Mole dig at $from: native surface stand or landing is unavailable"
                null
            } else GeneratedObstacle(EdgeType.CROSSING, null, from, from, spot.landing,
                handler = MoleCrossing.ID, cost = 3, bidirectional = false, itemId = MoleCrossing.SPADE,
                requirements = Requirements(items = listOf(ItemRequirement(MoleCrossing.SPADE)),
                    flags = setOf(MoleCrossing.LIGHT_READY)))
        }
    }
}
