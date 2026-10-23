package engine.obj

import api.predef.*
import io.luna.game.model.Position
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection

/**
 * Works out where stairs take players.
 *
 * @author TheLining
 */
object StairDestination {

    /**
     * The names stairs go by.
     */
    val NAMES = setOf("Staircase", "Stairs", "Spooky stairs", "Stone Staircase", "Cave Stairs", "Wooden Stair",
                      "Cellar stairs", "Steps")

    /**
     * How far the stairs leading back can be from the stairs being climbed, in tiles.
     */
    private const val PAIR_DISTANCE = 4

    /**
     * Returns where a player standing on [from] ends up after climbing [stairs], or `null` if they lead nowhere.
     *
     * [KnownStairs] go where they do in the original game. Otherwise stairs come in pairs, and the player lands in
     * front of the stairs that lead back (see [pair]), as close as possible to where they started.
     *
     * @param stairs The stairs.
     * @param from The climber's position.
     * @param up `true` to climb up, `false` to climb down.
     * @return The destination, or `null` if the stairs can't be climbed that way.
     */
    fun destination(stairs: GameObject, from: Position, up: Boolean): Position? {
        val known = KnownStairs.forStairs(stairs)
        if (known != null) {
            // Landings that depend on where the player stands use their own tile, like the original game.
            return (if (up) known.up else known.down)?.from(beside(stairs, from))
        }
        val tiles = LadderDestination.climbingTiles(stairs)
        val start = if (from in tiles) from else tiles.minByOrNull { it.computeLongestDistance(from) } ?: from
        val (_, shift, landing) = pair(stairs, up) ?: return null
        val target = start.translate(0, shift.dy, shift.dz)
        return if (target in landing) target else landing.minByOrNull { distanceSquared(it, target) }
    }

    /**
     * How climbing moves a player: [dy] tiles north and [dz] floors up.
     */
    private data class Shift(val dy: Int, val dz: Int)

    /**
     * The stairs that lead back, how far they are from the stairs being climbed, and the tiles a player can land on in
     * front of them.
     */
    private data class Pairing(val stairs: GameObject, val shift: Shift, val landing: List<Position>)

    /**
     * Returns the stairs that lead back after climbing [stairs], or `null` if there aren't any.
     *
     * These are the closest stairs one floor up or down, or in the dungeon below or above, with a free tile in front of
     * them. Failing that, stairs two floors away, which a few dungeons use.
     */
    private fun pair(stairs: GameObject, up: Boolean): Pairing? {
        val position = stairs.position
        val sign = if (up) 1 else -1
        val near = ArrayList<Shift>()
        near += Shift(0, sign)
        if (position.z == 0 && (position.y >= LadderType.CELLAR_OFFSET) == up) {
            near += Shift(-sign * LadderType.CELLAR_OFFSET, 0)
        }
        for (shifts in listOf(near, listOf(Shift(0, sign * 2)))) {
            val candidates = shifts.filter { Position.HEIGHT_LEVELS.contains(position.z + it.dz) }.flatMap { shift ->
                val base = position.translate(0, shift.dy, shift.dz)
                world.locator.findObjects(base, PAIR_DISTANCE) { it.def()?.name in NAMES && leadsBack(it, up) }
                    .map { it to shift }
            }
            val pairing = candidates.map { (other, shift) -> Pairing(other, shift, landingTiles(other)) }
                .filter { it.landing.isNotEmpty() }.minByOrNull { distanceSquared(stairs, it) }
            if (pairing != null) {
                return pairing
            }
        }
        return null
    }

    /**
     * Returns the tiles in front of [stairs] that a player can land on.
     */
    private fun landingTiles(stairs: GameObject) =
        LadderDestination.climbingTiles(stairs).filter { LadderDestination.canLand(it) }

    /**
     * Returns the squared distance between the centers of [stairs] and the [pairing]'s stairs, as if on the same map.
     */
    private fun distanceSquared(stairs: GameObject, pairing: Pairing): Int {
        val (x, y) = center(stairs)
        val (otherX, otherY) = center(pairing.stairs)
        val dx = otherX - x
        val dy = otherY - y - pairing.shift.dy * 2
        return dx * dx + dy * dy
    }

    /**
     * Returns `true` if [stairs] can be climbed back the other way after climbing [up].
     */
    private fun leadsBack(stairs: GameObject, up: Boolean) = directions(stairs).contains(!up)

    /**
     * Returns the ways [stairs] can be climbed: `true` for up, `false` for down.
     */
    fun directions(stairs: GameObject): Set<Boolean> {
        val directions = HashSet<Boolean>()
        for (action in stairs.def().actions.filterNotNull().map { it.lowercase() }) {
            when (action) {
                "climb-up", "walk-up", "ascend" -> directions += true
                "climb-down", "walk-down", "descend" -> directions += false
                "climb" -> {
                    directions += true
                    directions += false
                }
            }
        }
        return directions
    }

    /**
     * Returns twice the center of [stairs], so it stays a whole number.
     */
    private fun center(stairs: GameObject): Pair<Int, Int> {
        val (sizeX, sizeY) = size(stairs)
        return Pair(stairs.position.x * 2 + sizeX - 1, stairs.position.y * 2 + sizeY - 1)
    }

    /**
     * Returns [tile] if it's on [stairs] or right next to one of their sides, where players use them from, or the
     * closest tile that is.
     */
    private fun beside(stairs: GameObject, tile: Position): Position {
        val (sizeX, sizeY) = size(stairs)
        val position = stairs.position
        val x = tile.x.coerceIn(position.x - 1, position.x + sizeX)
        var y = tile.y.coerceIn(position.y - 1, position.y + sizeY)
        if (x !in position.x until position.x + sizeX && y !in position.y until position.y + sizeY) {
            // A corner: move beside the nearest side.
            y = y.coerceIn(position.y, position.y + sizeY - 1)
        }
        return Position(x, y, position.z)
    }

    /**
     * Returns the width and length of [stairs], turned with them.
     */
    private fun size(stairs: GameObject) =
        when (stairs.direction) {
            ObjectDirection.NORTH, ObjectDirection.SOUTH -> Pair(stairs.sizeY(), stairs.sizeX())
            else -> Pair(stairs.sizeX(), stairs.sizeY())
        }

    /**
     * Returns the squared distance between [a] and [b].
     */
    private fun distanceSquared(a: Position, b: Position): Int {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return dx * dx + dy * dy
    }
}
