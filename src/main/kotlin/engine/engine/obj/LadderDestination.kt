package engine.obj

import api.predef.*
import io.luna.game.model.Direction
import io.luna.game.model.EntityType
import io.luna.game.model.Position
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection

/**
 * Works out where ladders take players.
 *
 * @author TheLining
 */
object LadderDestination {

    /**
     * Returns where a player standing on [from] ends up after climbing [ladder], or `null` if it can't be climbed.
     *
     * The player keeps their x and y and moves by the ladder's [LadderType], unless it's a
     * [SpecialLadder]. Bots can interact diagonally, so the player climbs from the closest tile beside the ladder. The
     * #377 maps don't always line up either, so if the tile they'd land on has no floor or can't be walked onto, they
     * land beside the ladder on the other side instead.
     *
     * @param ladder The ladder.
     * @param from The climber's position.
     * @param up `true` to climb up, `false` to climb down.
     * @return The destination, or `null` if the ladder can't be climbed that way.
     */
    fun destination(ladder: GameObject, from: Position, up: Boolean): Position? {
        val start = if (canClimbFrom(ladder, from)) from else
            climbingTiles(ladder).minByOrNull { it.computeLongestDistance(from) } ?: from
        val special = SpecialLadder.forPosition(ladder.position)
        if (special != null) {
            val destination = special.destination
            val target = start.translate(destination.x - ladder.position.x, destination.y - ladder.position.y,
                                         destination.z - ladder.position.z)
            return if (canLand(destination)) destination else land(destination, target, up, special = true)
        }
        val type = LadderType.forObject(ladder)
        val destination = type.move(start, up) ?: return null
        val otherSide = type.move(ladder.position, up) ?: return null
        return if (otherSide.x < 0 || otherSide.y < 0) null else land(otherSide, destination, up)
    }

    /**
     * Returns where a climber heading for [target] lands. That's [target] itself (if it isn't a [special]
     * destination), or failing that beside the ladder on [otherSide], beside a ladder within two tiles that leads back,
     * or next to [otherSide].
     */
    private fun land(otherSide: Position, target: Position, up: Boolean, special: Boolean = false): Position? {
        if (!special && canLand(target)) {
            return target
        }
        val sameSpot = world.objects.findAll(otherSide).filter { it.def()?.name == "Ladder" }.findFirst().orElse(null)
        val other = sameSpot ?: world.locator
            .findObjects(otherSide, 2) { it.def()?.name == "Ladder" && leadsBack(it, up) }
            .minByOrNull { it.position.computeLongestDistance(otherSide) }
        val beside = other?.let { climbingTiles(it).filter { tile -> canLand(tile) } }.orEmpty()
        return when {
            target in beside -> target
            beside.isNotEmpty() -> beside.minByOrNull { it.computeLongestDistance(target) }
            // Nothing free beside it (an NPC can block its only tile), so keep the player's x and y.
            sameSpot != null && hasFloor(target) -> target
            else -> Direction.NESW.map { otherSide.translate(1, it) }.filter { canLand(it) }
                .minByOrNull { it.computeLongestDistance(target) }
        }
    }

    /**
     * Returns `true` if [ladder] can be climbed back the other way after climbing [up].
     */
    private fun leadsBack(ladder: GameObject, up: Boolean): Boolean {
        val actions = ladder.def().actions.filterNotNull().map { it.lowercase() }
        return "climb" in actions || (if (up) "climb-down" else "climb-up") in actions
    }

    /**
     * Returns the tiles [ladder] can be climbed from.
     */
    fun climbingTiles(ladder: GameObject): List<Position> {
        val position = ladder.position
        val (sizeX, sizeY) = size(ladder)
        val tiles = ArrayList<Position>()
        for (y in position.y until position.y + sizeY) {
            tiles += Position(position.x - 1, y, position.z)
            tiles += Position(position.x + sizeX, y, position.z)
        }
        for (x in position.x until position.x + sizeX) {
            tiles += Position(x, position.y - 1, position.z)
            tiles += Position(x, position.y + sizeY, position.z)
        }
        return tiles.filter { canClimbFrom(ladder, it) }
    }

    /**
     * Returns `true` if [tile] is beside [ladder], on a side it can be used from, with no wall in between.
     */
    private fun canClimbFrom(ladder: GameObject, tile: Position): Boolean {
        val position = ladder.position
        val (sizeX, sizeY) = size(ladder)
        val besideX = tile.x in position.x until position.x + sizeX
        val besideY = tile.y in position.y until position.y + sizeY
        val side = when {
            tile.z != position.z -> return false
            besideY && tile.x == position.x - 1 -> Direction.WEST
            besideX && tile.y == position.y + sizeY -> Direction.NORTH
            besideY && tile.x == position.x + sizeX -> Direction.EAST
            besideX && tile.y == position.y - 1 -> Direction.SOUTH
            else -> return false
        }

        // The definition's blocked sides (north 1, east 2, south 4, west 8), turned with the ladder like the client
        // does.
        val mask = ladder.def().direction
        val rotation = ladder.direction.id
        val blocked = if (rotation == 0) mask else (mask shl rotation and 0xf) + (mask shr 4 - rotation)
        val bit = when (side) {
            Direction.NORTH -> 1
            Direction.EAST -> 2
            Direction.SOUTH -> 4
            else -> 8
        }
        return (blocked and bit) == 0 &&
                world.collisionManager.traversable(tile.translate(1, side.opposite()), EntityType.PLAYER, side)
    }

    /**
     * Returns `true` if [tile] has a floor and a player can walk onto it.
     */
    fun canLand(tile: Position) =
        hasFloor(tile) && Direction.NESW.any {
            world.collisionManager.traversable(tile.translate(1, it), EntityType.PLAYER, it.opposite())
        }

    /**
     * Returns `true` if [tile] has a floor, rather than being a hole or the void.
     */
    private fun hasFloor(tile: Position): Boolean {
        val map = ctx.cache.mapIndexTable
        if (tile.x < 0 || tile.y < 0 || !map.indexTable.containsKey(tile.region)) {
            return false
        }
        // Bridges show the floor of the level above.
        val bridge = tile.z < 3 && map.getTile(Position(tile.x, tile.y, 1)).isBridge
        val floor = map.getTile(if (bridge) tile.translate(0, 0, 1) else tile)
        return floor.overlay != 0 || floor.underlay != 0
    }

    /**
     * Returns the width and length of [ladder], turned with the ladder.
     */
    private fun size(ladder: GameObject) =
        when (ladder.direction) {
            ObjectDirection.NORTH, ObjectDirection.SOUTH -> Pair(ladder.sizeY(), ladder.sizeX())
            else -> Pair(ladder.sizeX(), ladder.sizeY())
        }
}
