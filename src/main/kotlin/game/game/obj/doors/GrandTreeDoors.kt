package game.obj.doors

import api.predef.ext.*
import game.player.Sound
import game.skill.agility.AgilityAction
import game.skill.agility.AgilityApproach
import game.skill.agility.AgilityStep
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.World
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection
import io.luna.game.model.`object`.ObjectType

/**
 * Opens the Grand Tree's scenery doors using Lost City's area_gnome/gnome_gate passage.
 * Each two-tile leaf changes in place while its passenger crosses the still-solid open model.
 *
 * @author lare96
 */
object GrandTreeDoors {

    /**
     * The native closed/open models, placements and central walking lanes of the two leaves.
     *
     * @author lare96
     */
    enum class Leaf(val closed: Int, val open: Int, x: Int, val lane: Int, val direction: ObjectDirection) {
        LEFT(1967, 1969, 2464, 2465, ObjectDirection.WEST),
        RIGHT(1968, 1970, 2466, 2466, ObjectDirection.EAST);

        val position = Position(x, 3492)
        val south = Position(lane, 3491)
        val north = Position(lane, 3493)
    }

    private const val OPEN_TICKS = 5

    /**
     * Walks to the clicked leaf's central lane, then opens it and crosses in either direction.
     */
    fun use(player: Player, door: GameObject) {
        val leaf = Leaf.entries.firstOrNull {
            it.closed == door.id && it.position == door.position && it.direction == door.direction
        } ?: return
        if (door.objectType != ObjectType.DEFAULT || player.position.z != leaf.position.z ||
            !ready(player) || !current(player.world, door)) return
        val south = player.y <= leaf.position.y
        val start = if (south) leaf.south else leaf.north
        val destination = if (south) leaf.north else leaf.south
        if (player.position.computeLongestDistance(start) > AgilityApproach.RADIUS) return
        val begin = {
            if (ready(player) && player.position == start && current(player.world, door)) {
                cross(player, door, leaf, start, destination)
            }
        }
        if (player.position == start) begin() else player.submitAction(AgilityApproach(player, start, begin))
    }

    /**
     * Keeps the open replacement owned by this passage and restores it without overwriting a later object.
     */
    private fun cross(player: Player, door: GameObject, leaf: Leaf, start: Position, destination: Position) {
        val world = player.world
        var opened: GameObject? = null
        player.submitAction(AgilityAction(player, listOf(AgilityStep(destination, 2)), null, start = {
            if (world.removeObject(door)) {
                val replacement = world.addObject(leaf.open, door.position, door.objectType, door.direction)
                opened = replacement
                player.playSound(Sound.DOOR_OPEN)
                world.scheduleOnce(OPEN_TICKS) {
                    if (current(world, replacement) && world.removeObject(replacement)) {
                        world.addObject(door.id, door.position, door.objectType, door.direction)
                    }
                }
            }
        }, valid = {
            player.state == EntityState.ACTIVE && player.isAlive && player.position == start &&
                (opened?.let { current(world, it) } ?: (ready(player) && current(world, door)))
        }))
    }

    /**
     * Whether the player can begin a new passage or approach.
     */
    private fun ready(player: Player): Boolean =
        player.state == EntityState.ACTIVE && player.isAlive && !player.isLocked

    /**
     * Checks identity because object removal otherwise matches only the placement and shape.
     */
    private fun current(world: World, door: GameObject): Boolean =
        world.objects.findAll(door.position).anyMatch { it === door }
}
