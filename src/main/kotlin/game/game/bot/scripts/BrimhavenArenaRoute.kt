package game.bot.scripts

import api.predef.*
import game.content.brimhavenArena.ArenaObstacles
import game.content.brimhavenArena.ArenaTraps
import game.content.brimhavenArena.BrimhavenArena
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject
import kotlin.math.abs

/**
 * Plans one native crossing at a time over the arena's twenty-five platforms.
 * Rebuilding from live objects avoids stale plank lanes; native handlers alone decide traversal outcomes.
 *
 * @author lare96
 */
internal object BrimhavenArenaRoute {
    val platforms = (0..24).map { BrimhavenArena.tile(9 + it % 5 * 11, 10 + it / 5 * 11) }

    /**
     * A click obstacle or a walking trap, including safe approach and expected native landing tiles.
     *
     * @author lare96
     */
    data class Crossing(val from: Int, val to: Int, val start: Position, val end: Position,
                        val trigger: Position, val obj: GameObject?, val cost: Int)

    fun platform(position: Position): Int? = if (position.z != 3) null else platforms.indices
        .minByOrNull { platforms[it].computeLongestDistance(position) }
        ?.takeIf { platforms[it].computeLongestDistance(position) <= 4 }

    fun crossings(player: Player, objects: Collection<GameObject>): List<Crossing> = buildList {
        val live = objects.filter { it.state == EntityState.ACTIVE && BrimhavenArena.inside(it.position) && it.position.z == 3 }
        fun add(start: Position, end: Position, trigger: Position, obj: GameObject?, cost: Int) {
            val from = platform(start) ?: return
            val to = platform(end) ?: return
            if (abs(from % 5 - to % 5) + abs(from / 5 - to / 5) == 1)
                add(Crossing(from, to, start, end, trigger, obj, cost))
        }
        for (obj in live.filter { it.id in ArenaObstacles.ids }) {
            for (origin in platforms.filter { it.computeLongestDistance(obj.position) <= 6 }) {
                val route = ArenaObstacles.route(player, obj, 0.0, origin) ?: continue
                if (route.failed || platform(route.start) != platforms.indexOf(origin)) continue
                add(route.start, route.steps.last().destination, route.start, obj, route.steps.sumOf { it.ticks } + 7)
            }
        }
        for (trap in ArenaTraps.traps.values) {
            if (player.agility.level < trap.level || live.none { it.id == trap.objectId && it.position == trap.anchor }) continue
            add(trap.failure, trap.destination, trap.start, null, if (trap.objectId == 3581) 20 else 12)
        }
        for ((first, second) in ArenaTraps.bladeTiles) {
            if (live.none { it.id == 3568 && it.position == first }) continue
            val dx = second.x - first.x; val dy = second.y - first.y
            val start = first.translate(-dx, -dy); val end = second.translate(dx, dy)
            add(start, end, end, null, 14); add(end, start, start, null, 14)
        }
    }.distinctBy { listOf(it.from, it.to, it.start, it.end, it.obj?.id) }

    /**
     * Returns the cheapest remaining path, or null when no currently usable route reaches the goal.
     */
    fun path(from: Int, to: Int, crossings: List<Crossing>): List<Crossing>? {
        if (from !in platforms.indices || to !in platforms.indices) return null
        val costs = IntArray(25) { Int.MAX_VALUE }; costs[from] = 0
        val previous = arrayOfNulls<Crossing>(25)
        val remaining = platforms.indices.toMutableSet()
        while (remaining.isNotEmpty()) {
            val current = remaining.minBy { costs[it] }
            if (costs[current] == Int.MAX_VALUE) return null
            if (current == to) {
                val result = mutableListOf<Crossing>()
                var node = to
                while (node != from) { val edge = previous[node] ?: return null; result += edge; node = edge.from }
                return result.reversed()
            }
            remaining.remove(current)
            for (edge in crossings.filter { it.from == current && it.to in remaining }) {
                val cost = costs[current] + edge.cost
                if (cost < costs[edge.to]) { costs[edge.to] = cost; previous[edge.to] = edge }
            }
        }
        return null
    }
}
