package game.skill.agility

import engine.obj.LadderDestination
import game.player.Sound
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.EntityState
import io.luna.game.model.mob.MobDeathAction
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.ExactMovement
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection
import io.luna.game.model.path.PathResultType
import io.luna.game.model.path.route.RoutePathfinder
import io.luna.game.model.path.route.RouteStrategy

/**
 * The dense woodland passages in Isafdar, which need level 56 Agility in either direction.
 *
 * @author lare96
 */
object IsafdarForest {

    const val REQUIRED_LEVEL = 56
    val IDS = setOf(3937, 3938, 3939)

    /**
     * Finds the native start and finish tiles on opposite sides of the rotated woodland object.
     */
    fun sides(position: Position, direction: ObjectDirection): Pair<Position, Position> =
        if (direction == ObjectDirection.NORTH || direction == ObjectDirection.SOUTH) {
            position.translate(-1, 1) to position.translate(2, 1)
        } else {
            position.translate(1, -1) to position.translate(1, 2)
        }

    /**
     * Selects the native movement direction from the player's side of the object, before approaching its opening.
     */
    fun route(forest: GameObject, from: Position): Pair<Position, Position> {
        val (first, second) = sides(forest.position, forest.direction)
        val forward = if (forest.direction == ObjectDirection.NORTH || forest.direction == ObjectDirection.SOUTH) {
            from.x < forest.position.x
        } else {
            from.y < forest.position.y
        }
        return if (forward) first to second else second to first
    }

    /**
     * Finds a walkable stand beside a native endpoint that can interact with this passage and be walked to after
     * landing. The exact endpoint itself may be inside scenery and must not be required as an ordinary walking tile.
     */
    fun approach(forest: GameObject, start: Position): Position? {
        val collision = forest.world.collisionManager
        val pathfinder = RoutePathfinder(collision, 1, 0, RouteStrategy.NORMAL)
        val candidates = (-1..1).flatMap { dx -> (-1..1).map { dy -> start.translate(dx, dy) } }
            .sortedWith(compareBy<Position> { it.computeLongestDistance(start) }
                .thenBy { kotlin.math.abs(it.x - start.x) + kotlin.math.abs(it.y - start.y) }
                .thenBy { it.x }.thenBy { it.y })
        return candidates.firstOrNull { stand ->
            route(forest, stand).first == start && LadderDestination.canLand(stand) &&
                collision.reached(stand, forest, InteractionPolicy.STANDARD_SIZE) &&
                (stand == start || pathfinder.find(start, stand).type == PathResultType.COMPLETE)
        }
    }

    /**
     * Approaches the opening before squeezing through with its native animation and timing.
     */
    fun enter(plr: Player, forest: GameObject) {
        if (forest.id !in IDS || forest.position.z != plr.z || plr.isLocked || !plr.canTraverseIsafdar() ||
            !Agility.checkLevel(plr, REQUIRED_LEVEL)) return
        // TODO Once Regicide exists, passing the forest also needs the elven tracker's directions.
        val (start, destination) = route(forest, plr.position)
        if (plr.position.computeLongestDistance(start) > AgilityApproach.RADIUS) return
        val animation = when (forest.id) {
            3937 -> 839
            3938 -> 749
            else -> 1237
        }
        val sound = when (forest.id) {
            3937 -> Sound.FOREST_LOWWALL
            3938 -> Sound.FOREST_DOUBLESQUEEZE
            else -> Sound.FOREST_SIDESQUEEZE
        }
        val enter = {
            if (!plr.isLocked && plr.canTraverseIsafdar() && forest.state == EntityState.ACTIVE &&
                forest.isVisibleTo(plr) && plr.z == forest.position.z &&
                route(forest, plr.position).first == start && plr.position.computeLongestDistance(start) <= 1 &&
                Agility.checkLevel(plr, REQUIRED_LEVEL)) {
                val entry = plr.position
                plr.submitAction(AgilityAction(plr, listOf(AgilityStep(destination, 4, animation = animation,
                    animationTick = 1, moveStart = 30, moveEnd = 94,
                    exactStart = start, begin = { IsafdarForestMerge.send(plr, forest) },
                    sounds = listOf(AgilitySound(sound, delayCycles = if (forest.id == 3939) 40 else 30)))),
                    "You make your way through the dense forest.", valid = {
                        plr.canTraverseIsafdar() && plr.position == entry && forest.state == EntityState.ACTIVE &&
                            forest.isVisibleTo(plr)
                    }).also { action ->
                    action.onCancel = {
                        plr.exactMove(ExactMovement(plr.lastRegion, plr.position, plr.position, 0, 1,
                            Direction.between(start, destination)))
                        IsafdarForestMerge.cancel(plr, forest)
                    }
                })
            }
        }
        if (plr.position.computeLongestDistance(start) <= 1) {
            enter()
        } else {
            val stand = approach(forest, start) ?: return
            plr.submitAction(AgilityApproach(plr, stand) { enter() })
        }
    }
}

/**
 * Rejects a traversal that outlives its player or an unfinished death sequence.
 */
internal fun Player.canTraverseIsafdar(): Boolean = state == EntityState.ACTIVE && isAlive &&
    actions.getAll(MobDeathAction::class.java).none { !it.isFinished }
