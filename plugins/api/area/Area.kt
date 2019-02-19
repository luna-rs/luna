package api.area

import api.predef.rand
import io.luna.game.model.*
import io.luna.game.model.mob.Player
import javafx.geometry.Pos
import java.util.*

/**
 * A model representing a square-shaped 2D/3D area on the map.
 *
 * @author lare96 <http://github.org/lare96>
 */
abstract class Area {

    /**
     * The south-west x coordinate.
     */
    val southWestX: Int

    /**
     * The south-west y coordinate.
     */
    val southWestY: Int

    /**
     * The north-east x coordinate.
     */
    val northEastX: Int

    /**
     * The north-east y coordinate.
     */
    val northEastY: Int

    /**
     * The z coordinate range.
     */
    val z: IntRange

    /**
     *
     */
    constructor(southWestX: Int, southWestY: Int, northEastX: Int, northEastY: Int, z: IntRange = 0..3) {
        require(northEastX >= southWestX) { "northEastX cannot be smaller than southWestX" }
        require(northEastY >= southWestY) { "northEastY cannot be smaller than southWestY" }
        require(southWestX >= 0 && southWestY >= 0 &&
                z.last <= Position.HEIGHT_LEVELS.upperEndpoint() &&
                z.first >= Position.HEIGHT_LEVELS.lowerEndpoint())
        { "Parameters must be within the bounds of a Position" }

        this.southWestX = southWestX
        this.southWestY = southWestY
        this.northEastX = northEastX
        this.northEastY = northEastY
        this.z = z
    }

    override fun hashCode(): Int =
            Objects.hash(southWestX, southWestY, northEastX, northEastY, z)

    override fun equals(other: Any?): Boolean =
            when (other) {
                is Area -> southWestX == other.southWestX &&
                        southWestY == other.southWestY &&
                        northEastX == other.northEastX &&
                        northEastY == other.northEastY &&
                        z == other.z
                else -> false
            }

    abstract fun enter(plr: Player)
    abstract fun exit(plr: Player)

    /**
     * Determines if this area contains [position]. Runs in O(1) time.
     */
    fun contains(position: Position) =
            position.x >= southWestX &&
                    position.x <= northEastX &&
                    position.y >= southWestY &&
                    position.y <= northEastY &&
                    z.contains(position.z)

    /**
     * Determines if [entity] is within this area. Runs in O(1) time.
     */
    fun contains(entity: Entity) = contains(entity.position)

    /**
     * Computes and returns **new** list of positions that make up this area.
     */
    fun toList(): List<Position> {
        val positions = ArrayList<Position>(size() * z.last)
        for (x in southWestX..northEastX) {
            for (y in southWestY..northEastY) {
                for (plane in z) {
                    positions += Position(x, y, plane)
                }
            }
        }
        return positions
    }

    /**
     * Returns a random position from this area.
     */
    fun random(): Position {
        val randomX = rand(northEastX - southWestX) + southWestX
        val randomY = rand(northEastY - southWestY) + southWestY
        val randomZ = rand(z.first, z.last)
        return Position(randomX, randomY, randomZ)
    }

    /**
     * Returns the center of this area.
     */
    fun center(): Position {
        val halfWidth = width() / 2
        val centerX = southWestX + halfWidth
        val centerY = southWestY + halfWidth
        return Position(centerX, centerY)
    }

    /**
     * Returns the length of this area.
     */
    fun length() // Areas are inclusive to base coordinates, so we add 1.
            = (northEastY - southWestY) + 1

    /**
     * Returns the width of this area.
     */
    fun width() // Areas are inclusive to base coordinates, so we add 1.
            = (northEastX - southWestX) + 1

    /**
     * Returns the size of this area on a single plane.
     */
    fun size() = length() * width()

    /**
     * Returns the size of this area on all planes.
     */
   fun totalSize() = size() * (z.last + 1)
}
