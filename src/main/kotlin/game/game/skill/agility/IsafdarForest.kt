package game.skill.agility

import game.player.Sound
import io.luna.game.model.Position
import io.luna.game.model.EntityState
import io.luna.game.model.mob.MobDeathAction
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection

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
     * Approaches the opening before squeezing through with its native animation and timing.
     */
    fun enter(plr: Player, forest: GameObject) {
        if (forest.id !in IDS || forest.position.z != plr.z || plr.isLocked || !plr.canTraverseIsafdar() ||
            !Agility.checkLevel(plr, REQUIRED_LEVEL)) return
        // TODO Once Regicide exists, passing the forest also needs the elven tracker's directions.
        val (first, second) = sides(forest.position, forest.direction)
        val start = if (plr.position.computeLongestDistance(first) <= plr.position.computeLongestDistance(second)) first else second
        if (plr.position.computeLongestDistance(start) > AgilityApproach.RADIUS) return
        val destination = if (start == first) second else first
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
            if (!plr.isLocked && plr.canTraverseIsafdar() && plr.position == start &&
                Agility.checkLevel(plr, REQUIRED_LEVEL)) {
                plr.submitAction(AgilityAction(plr, listOf(AgilityStep(destination, 4, animation = animation,
                    animationTick = 1, moveStart = 30, moveEnd = 94,
                    sounds = listOf(AgilitySound(sound, delayCycles = if (forest.id == 3939) 40 else 30)))),
                    "You make your way through the dense forest.", valid = { plr.canTraverseIsafdar() }))
            }
        }
        if (plr.position == start) enter() else plr.submitAction(AgilityApproach(plr, start) { enter() })
    }
}

/**
 * Rejects a traversal that outlives its player or an unfinished death sequence.
 */
internal fun Player.canTraverseIsafdar(): Boolean = state == EntityState.ACTIVE && isAlive &&
    actions.getAll(MobDeathAction::class.java).none { !it.isFinished }
