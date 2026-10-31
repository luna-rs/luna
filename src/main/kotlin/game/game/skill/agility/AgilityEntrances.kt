package game.skill.agility

import api.predef.*
import game.skill.Skills
import game.player.Sound
import io.luna.game.model.Position
import io.luna.game.model.mob.Player

/**
 * Entrance traversals that award XP separately from the course checkpoints.
 *
 * @author lare96
 */
object AgilityEntrances {

    /**
     * Only entering the Barbarian course requires level 35; a drained player can still leave.
     */
    fun barbarianPipe(plr: Player): AgilityAction? {
        if (plr.isLocked || plr.z != 0 || plr.x != 2552 || plr.y !in setOf(3558, 3561)) return null
        val entering = plr.y == 3561
        if (entering && !Agility.checkLevel(plr, 35)) return null
        return AgilityAction(plr,
            listOf(AgilityStep(Position(2552, if (entering) 3558 else 3561), 4, animation = 749,
                animationTick = 1, moveStart = 30, sounds = listOf(AgilitySound(Sound.SQUEEZE_IN, tick = 4)))),
            "You squeeze through the pipe.").also {
            it.onSuccess = { plr.agility.addExperience(10.0) }
        }
    }

    /**
     * The Wilderness ridge requires level 52 on entry. Falling lands in the wolf pit without
     * damage or XP, as in Lost City's fail_ridge label; returning across the ridge has no level gate.
     */
    fun wildernessRidge(plr: Player, entering: Boolean,
                        roll: () -> Double = { rand().nextDouble() }): AgilityAction? {
        if (plr.isLocked || plr.z != 0 || plr.x !in 2997..2998) return null
        if (entering && plr.y !in 3916..3917 || !entering && plr.y !in 3931..3932) return null
        if (entering && !Agility.checkLevel(plr, 52)) return null
        val middle = Position(2998, if (entering) 3925 else 3924)
        val failed = roll() >= Skills.successRate(200, 250, minOf(plr.agility.level, 99))
        val steps = mutableListOf(
            AgilityStep(Position(2998, if (entering) 3918 else 3930), 2,
                walkingAnimation = AgilityWalk.NORMAL, sounds = listOf(AgilitySound(Sound.DOOR_OPEN))),
            AgilityStep(middle, if (entering) 7 else 6, walkingAnimation = 762)
        )
        if (failed) {
            // The falling pose is set a tick before the fall's exact movement, which keeps the pose it starts with.
            steps += AgilityStep(middle, 1, walkingAnimation = 768, animation = 764, jump = true,
                sounds = listOf(AgilitySound(Sound.STUMBLE_LOOP, loops = 10)))
            steps += AgilityStep(middle.translate(3, -1), 2)
        } else {
            steps += AgilityStep(Position(2998, if (entering) 3931 else 3918), 6, walkingAnimation = 762)
            steps += AgilityStep(Position(2998, if (entering) 3933 else 3916), 2,
                walkingAnimation = AgilityWalk.NORMAL,
                sounds = listOf(AgilitySound(Sound.DOOR_OPEN)))
        }
        return AgilityAction(plr, steps, "You try to edge over the ridge...").also {
            it.onSuccess = {
                if (failed) plr.sendMessage("You lose your footing and fall into the wolf pit.")
                else {
                    plr.sendMessage("You skillfully balance across the ridge.")
                    plr.agility.addExperience(15.0)
                }
            }
        }
    }

    /**
     * The Taverley Dungeon pipe between the main cavern and the blue dragons. It needs level 70 both ways and gives
     * no XP.
     */
    const val TAVERLEY_PIPE = 9293
    const val TAVERLEY_PIPE_LEVEL = 70
    val TAVERLEY_PIPE_WEST = Position(2886, 9799)
    val TAVERLEY_PIPE_EAST = Position(2892, 9799)

    /**
     * The Taverley Dungeon strange floor, a jump over spikes into the western section. It needs level 80 both ways and
     * gives no XP.
     */
    const val TAVERLEY_FLOOR = 9294
    const val TAVERLEY_FLOOR_LEVEL = 80
    val TAVERLEY_FLOOR_WEST = Position(2878, 9813)
    val TAVERLEY_FLOOR_EAST = Position(2880, 9813)

    /**
     * Squeezes through the Taverley pipe from whichever end is closer, in two halves on either side of the gap
     * between its two pipe objects.
     */
    fun taverleyPipe(plr: Player): AgilityAction? {
        val start = closerEnd(plr, TAVERLEY_PIPE_WEST, TAVERLEY_PIPE_EAST) ?: return null
        if (!Agility.checkLevel(plr, TAVERLEY_PIPE_LEVEL)) return null
        val end = if (start == TAVERLEY_PIPE_WEST) TAVERLEY_PIPE_EAST else TAVERLEY_PIPE_WEST
        plr.move(start)
        val middle = Position((start.x + end.x) / 2, start.y)
        return AgilityAction(plr, listOf(AgilityStep(middle, 4, animation = 749),
            AgilityStep(end, 4, animation = 749, animationTick = 1)), "You squeeze through the pipe.")
    }

    /**
     * Jumps over the Taverley strange floor from whichever side is closer.
     */
    fun taverleyStrangeFloor(plr: Player): AgilityAction? {
        val start = closerEnd(plr, TAVERLEY_FLOOR_WEST, TAVERLEY_FLOOR_EAST) ?: return null
        if (!Agility.checkLevel(plr, TAVERLEY_FLOOR_LEVEL)) return null
        val end = if (start == TAVERLEY_FLOOR_WEST) TAVERLEY_FLOOR_EAST else TAVERLEY_FLOOR_WEST
        plr.move(start)
        return AgilityAction(plr, listOf(AgilityStep(end, 2, animation = 741)), "You jump over the strange floor.")
    }

    /**
     * Finds the end of a two-ended shortcut that [plr] is clicking from. The click can land beside the end tile, so
     * anything within two tiles counts.
     */
    private fun closerEnd(plr: Player, west: Position, east: Position): Position? {
        if (plr.isLocked || plr.z != 0) return null
        val start = if (plr.position.computeLongestDistance(west) <= plr.position.computeLongestDistance(east)) west
        else east
        return if (plr.position.computeLongestDistance(start) <= 2) start else null
    }
}
