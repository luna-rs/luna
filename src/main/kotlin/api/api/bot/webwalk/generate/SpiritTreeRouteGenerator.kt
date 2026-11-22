package api.bot.webwalk.generate

import api.bot.webwalk.model.EdgeType
import api.bot.webwalk.walk.crossing.SpiritTreeCrossing
import api.predef.world
import engine.obj.LadderDestination
import game.content.spiritTrees.SpiritTree
import io.luna.game.model.mob.interact.InteractionPolicy

/**
 * Resolves the spirit trees from the loaded map and links the destinations they offer in dialogue.
 *
 * @author lare96
 */
object SpiritTreeRouteGenerator {

    /**
     * Requires a reachable Talk-to stand at the source and a legal native landing at the destination.
     */
    fun gather(report: GenerationReport): List<GeneratedObstacle> = buildList {
        val sites = SpiritTree.entries
        val trees = world.objects.staticIterator().asSequence().filter { obj -> sites.any { it.objectId == obj.id } }
            .groupBy { it.id }
        val landings = sites.filter { LadderDestination.canLand(it.landing) }.toSet()
        for (from in sites) {
            val tree = trees[from.objectId]?.singleOrNull { it.position.isWithinDistance(from.landing, 8) }
            val stand = tree?.let { obj ->
                (listOf(from.landing) + EntranceStands.of(obj))
                    .firstOrNull { tile ->
                        // A bot's corrective step from a diagonal can interrupt the pending Talk-to click.
                        val position = obj.position
                        val needsStep = tile == position || tile.isWithinDistance(position, 1) &&
                            tile.x != position.x && tile.y != position.y
                        !needsStep && LadderDestination.canLand(tile) &&
                            world.collisionManager.reached(tile, obj, InteractionPolicy.STANDARD_SIZE)
                    }
            }
            if (tree == null || stand == null) {
                report.skipped += "spirit tree $from: missing tree or Talk-to stand"
                continue
            }
            for (to in from.destinations) {
                if (to !in landings) {
                    report.skipped += "spirit tree $from to $to: missing legal landing"
                    continue
                }
                add(GeneratedObstacle(EdgeType.CROSSING, tree.id, tree.position, stand, to.landing,
                    option = "Talk-to", handler = SpiritTreeCrossing.ID, cost = 12, bidirectional = false))
                report.count("spirit tree trips")
            }
        }
    }
}
