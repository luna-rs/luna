package api.bot.webwalk.generate

import api.bot.webwalk.model.EdgeType
import api.bot.webwalk.model.Requirements
import api.bot.webwalk.walk.crossing.CanoeCrossing
import api.predef.world
import engine.obj.LadderDestination
import game.content.canoes.CanoeStation
import game.content.canoes.CanoeType

/**
 * Links River Lum stations using the smallest canoe that can make each directed trip.
 *
 * @author lare96
 */
object CanoeRouteGenerator {

    /**
     * Resolves the loaded station tree and verifies the native work tiles and destination landings.
     */
    fun gather(report: GenerationReport): List<GeneratedObstacle> = buildList {
        val stations = CanoeStation.entries
        val trees = world.objects.staticIterator().asSequence().filter { CanoeStation.forTree(it.id) != null }
            .groupBy { it.id }
        val landings = stations.filter { LadderDestination.canLand(it.landing) }.toSet()
        for (from in stations) {
            val station = from.tree ?: continue
            val tree = trees[station.id]?.singleOrNull { it.position.isWithinDistance(station.chopTile, 4) }
            if (tree == null || !LadderDestination.canLand(station.chopTile) ||
                !LadderDestination.canLand(station.shapeTile)) {
                report.skipped += "canoe $from: missing tree or work tiles"
                continue
            }
            for (to in stations) {
                val type = CanoeType.entries.firstOrNull { to in from.destinations(it) } ?: continue
                if (to !in landings) {
                    report.skipped += "canoe $from to $to: missing legal landing"
                    continue
                }
                val cost = if (to == CanoeStation.WILDERNESS_POND) 60 else 68
                add(GeneratedObstacle(EdgeType.CROSSING, tree.id, tree.position, station.chopTile, to.landing,
                    handler = CanoeCrossing.ID, cost = cost, bidirectional = false,
                    requirements = Requirements(flags = setOf(CanoeCrossing.flag(type)))))
                report.count("canoe trips")
            }
        }
    }
}
