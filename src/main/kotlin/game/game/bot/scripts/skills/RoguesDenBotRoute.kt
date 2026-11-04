package game.bot.scripts.skills

import api.predef.world
import game.skill.agility.roguesDen.RoguesDenMap
import io.luna.game.model.Direction
import io.luna.game.model.EntityType
import io.luna.game.model.Position
import io.luna.game.model.`object`.GameObject
import java.util.PriorityQueue

/**
 * Plans collision-checked maze walks and the native one-way obstacle crossings between its rooms.
 * Geometry is captured once, while each attempt keeps its own route and remaining skill-point budget.
 *
 * @author lare96
 */
internal object RoguesDenBotRoute {

    /**
     * A native interaction and its required standing tile and observed landing.
     *
     * @author lare96
     */
    data class Step(val from: Position, val to: Position, val objectId: Int = -1,
                    val objectTile: Position? = null, val agility: Int = 0, val thieving: Int = 0)

    /**
     * A complete leg and its conservative skill-point cost, including searched traps and walking crossings.
     *
     * @author lare96
     */
    data class Plan(val steps: List<Step>, val agility: Int, val thieving: Int)

    /**
     * Immutable shared edges; moving guards and per-player disarming remain execution-time checks.
     *
     * @author lare96
     */
    class Geometry(val edges: Map<Position, List<Step>>) {

        /**
         * Chooses a bounded low-cost route without spending either skill beyond its current level.
         */
        fun plan(start: Position, goals: Set<Position>, agility: Int, thieving: Int,
                 canPickLocks: Boolean): Plan? {
            /**
             * A search label with identity-based retirement and a non-recursive predecessor link.
             *
             * @author lare96
             */
            class Label(val at: Position, val score: Int, val agility: Int, val thieving: Int,
                        val previous: Label?, val step: Step?) {
                var active = true
            }
            val queue = PriorityQueue<Label>(compareBy { it.score })
            val labels = HashMap<Position, MutableList<Label>>()
            val first = Label(start, 0, 0, 0, null, null)
            queue += first
            labels[start] = arrayListOf(first)
            var expanded = 0
            while (queue.isNotEmpty() && expanded++ < 100_000) {
                val current = queue.remove()
                if (!current.active) continue
                if (current.at in goals) {
                    val steps = ArrayList<Step>()
                    var cursor = current
                    while (cursor.step != null) {
                        steps += cursor.step!!
                        cursor = cursor.previous!!
                    }
                    return Plan(steps.asReversed(), current.agility, current.thieving)
                }
                for (edge in edges[current.at].orEmpty()) {
                    if (edge.objectId == 7246 && !canPickLocks) continue
                    val a = current.agility + edge.agility
                    val t = current.thieving + edge.thieving
                    if (a > agility || t > thieving) continue
                    val score = current.score + 1 + 30 * (edge.agility + edge.thieving)
                    val existing = labels.getOrPut(edge.to) { ArrayList() }
                    if (existing.any { it.score <= score && it.agility <= a && it.thieving <= t }) continue
                    existing.removeIf {
                        val dominated = it.score >= score && it.agility >= a && it.thieving >= t
                        if (dominated) it.active = false
                        dominated
                    }
                    val next = Label(edge.to, score, a, t, current, edge)
                    existing += next
                    queue += next
                }
            }
            return null
        }
    }

    private var geometry: Geometry? = null

    /**
     * Returns geometry from this runtime's native map, after its trap registry has loaded.
     */
    fun geometry(): Geometry? {
        geometry?.let { return it }
        if (RoguesDenMap.trapsAt.isEmpty()) return null
        val objects = world.objects.staticIterator().asSequence().filter {
            RoguesDenMap.inBox(it.position) && it.position.z == 1
        }.toList()
        val specials = HashMap<Position, MutableList<Step>>()
        fun edge(from: Position, to: Position, obj: GameObject, agility: Int = 0, thieving: Int = 0) {
            specials.getOrPut(from) { ArrayList() } += Step(from, to, obj.id, obj.position, agility, thieving)
        }
        for (obj in objects) {
            val at = obj.position
            val direction = obj.direction.toNormalDirection()
            val beyond = at.translate(1, direction)
            when (obj.id) {
                7255, 7232, 7234 -> edge(at, beyond, obj)
                7246 -> edge(at, beyond, obj, thieving = 10)
                7219 -> {
                    val partner = objects.firstOrNull {
                        it.id == 7219 && it.position == at.translate(2, direction.opposite())
                    } ?: continue
                    edge(beyond, partner.position.translate(1, partner.direction.toNormalDirection()), obj)
                }
                7251 -> {
                    val directionAcross = if (obj.direction.id % 2 == 1) Direction.EAST else Direction.NORTH
                    val first = at.translate(1, directionAcross)
                    val second = at.translate(1, directionAcross.opposite())
                    edge(first, second, obj, agility = 5)
                    edge(second, first, obj, agility = 5)
                }
            }
        }
        // These are the native LEDGES endpoints, not freely walkable shortcuts across their animations.
        for ((from, to, cost) in listOf(
            Triple(Position(2958, 5030, 1), Position(2958, 5035, 1), 4),
            Triple(Position(2994, 5004, 1), Position(2988, 5004, 1), 4),
            Triple(Position(2994, 5005, 1), Position(2988, 5005, 1), 4),
            Triple(Position(2982, 5087, 1), Position(2991, 5087, 1), 7),
            Triple(Position(2982, 5090, 1), Position(2991, 5090, 1), 7))) {
            val ledge = objects.filter { it.id == 7239 || it.id == 7240 }.filter {
                it.x in minOf(from.x, to.x)..maxOf(from.x, to.x) &&
                    it.y in minOf(from.y, to.y)..maxOf(from.y, to.y) && it.position != from && it.position != to
            }
            val forward = ledge.minByOrNull { it.position.computeLongestDistance(from) } ?: continue
            val reverse = ledge.minByOrNull { it.position.computeLongestDistance(to) } ?: continue
            edge(from, to, forward, agility = cost)
            edge(to, from, reverse, agility = cost)
        }
        val edges = HashMap<Position, List<Step>>()
        for (x in 2944..3071) for (y in 4992..5119) {
            val from = Position(x, y, 1)
            if (dangerous(from)) continue
            val next = ArrayList<Step>()
            for (direction in Direction.NESW) {
                val to = from.translate(1, direction)
                if (!RoguesDenMap.inBox(to) || dangerous(to) ||
                    !world.collisionManager.traversable(from, EntityType.PLAYER, direction)) continue
                val crossing = RoguesDenMap.crossings[to]
                val agility = if (crossing != null && crossing != RoguesDenMap.crossings[from]) 3 else 0
                val before = RoguesDenMap.traps[from].orEmpty().map { it.obj.position }.toSet()
                val thieving = RoguesDenMap.traps[to].orEmpty().count { it.obj.position !in before }
                next += Step(from, to, agility = agility, thieving = thieving)
            }
            next += specials[from].orEmpty().filter { !dangerous(it.to) && RoguesDenMap.traps[it.to].isNullOrEmpty() }
            if (next.isNotEmpty()) edges[from] = next
        }
        return Geometry(edges).also { geometry = it }
    }

    /**
     * Avoids stationary blades and the entire moving-blade perimeter, so timing cannot invalidate a walking leg.
     */
    private fun dangerous(tile: Position): Boolean = tile in RoguesDenMap.blades ||
        (tile.x in 3044..3059 && tile.y in 5090..5105 &&
            (tile.x == 3044 || tile.x == 3059 || tile.y == 5090 || tile.y == 5105))

    /**
     * Reverses the native puzzle's at-most-three scramble turns using a bounded search over visible dial states.
     */
    fun solveDials(dials: IntArray): List<Pair<Int, Boolean>>? {
        if (dials.size != 9 || dials.any { it !in 0..3 }) return null
        fun encode(values: IntArray): Int = values.foldIndexed(0) { index, bits, value -> bits or (value shl (index * 2)) }
        fun turn(bits: Int, index: Int, clockwise: Boolean): Int {
            val values = IntArray(9) { (bits ushr (it * 2)) and 3 }
            val change = if (clockwise) 1 else 3
            values[index] = (values[index] + change) and 3
            for (near in 0..8) if (kotlin.math.abs(near / 3 - index / 3) +
                kotlin.math.abs(near % 3 - index % 3) == 1) values[near] = (values[near] + 4 - change) and 3
            return encode(values)
        }
        val first = encode(dials)
        if (first == 0) return emptyList()
        val queue = ArrayDeque<Pair<Int, List<Pair<Int, Boolean>>>>()
        val seen = HashSet<Int>()
        queue += first to emptyList()
        seen += first
        while (queue.isNotEmpty()) {
            val (state, path) = queue.removeFirst()
            if (path.size == 3) continue
            for (index in 0..8) for (clockwise in listOf(true, false)) {
                val next = turn(state, index, clockwise)
                val answer = path + (index to clockwise)
                if (next == 0) return answer
                if (seen.add(next)) queue += next to answer
            }
        }
        return null
    }
}
