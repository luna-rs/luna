package api.bot.webwalk.generate

import api.bot.webwalk.model.EdgeType
import api.bot.webwalk.model.ItemRequirement
import api.bot.webwalk.model.Requirements
import api.bot.webwalk.walk.crossing.ContextualItemCrossing
import engine.obj.LadderDestination
import game.item.teleport.ContextualTravelItems
import game.item.teleport.MysticJewel
import io.luna.game.model.Position

/**
 * Generates source-constrained whistle, bell and maze-exit interactions from real legal stand tiles.
 * These are local inventory crossings, so a whistle cannot become a global teleport or ignore its location rules.
 * A missing native floor omits the corresponding edge and records the reason for review.
 *
 * @author lare96
 */
object ContextualItemRouteGenerator {
    /**
     * Adds only the directions supported by the native items, without quest automation or new movement effects.
     */
    fun gather(report: GenerationReport, canLand: (Position) -> Boolean = LadderDestination::canLand): List<GeneratedObstacle> = buildList {
        fun addRoute(item: Int, candidates: List<Position>, destination: Position, allowed: (Position) -> Boolean) {
            val from = candidates.firstOrNull { allowed(it) && canLand(it) }
            if (from == null || !canLand(destination)) {
                report.skipped += "contextual item $item to $destination: no legal native stand or landing"
                return
            }
            add(GeneratedObstacle(EdgeType.CROSSING, null, from, from, destination,
                handler = ContextualItemCrossing.ID, cost = 12, bidirectional = false, itemId = item,
                requirements = Requirements(items = listOf(ItemRequirement(item)))))
            report.count("Contextual inventory crossings")
        }
        addRoute(ContextualTravelItems.WHISTLE, listOf(ContextualTravelItems.MAINLAND_LANDING),
            ContextualTravelItems.REALM_LANDING) {
            it.z == 0 && ContextualTravelItems.WHISTLE_MAINLAND.contains(it)
        }
        addRoute(ContextualTravelItems.WHISTLE, listOf(ContextualTravelItems.REALM_LANDING),
            ContextualTravelItems.MAINLAND_LANDING) {
            it.z <= 2 && ContextualTravelItems.WHISTLE_REALM.contains(it)
        }
        addRoute(ContextualTravelItems.BELL, buildList {
            for (y in 4694..4697) for (x in 2759..2764) add(Position(x, y))
        }, ContextualTravelItems.BELL_LANDING) { stand ->
            ContextualTravelItems.BELL_BRICKS.any { it.isWithinDistance(stand, ContextualTravelItems.BELL_RANGE) }
        }
        addRoute(MysticJewel.ID, buildList {
            for (y in MysticJewel.START.y + 2..MysticJewel.START.y + 8)
                for (x in MysticJewel.START.x - 3..MysticJewel.START.x + 3) add(Position(x, y, 1))
        }, MysticJewel.START, MysticJewel::inMaze)
    }
}
