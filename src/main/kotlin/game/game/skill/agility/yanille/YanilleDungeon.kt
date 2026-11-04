package game.skill.agility.yanille

import api.predef.*
import game.skill.Skills
import game.skill.agility.AgilityAction
import game.skill.agility.AgilityStep
import game.skill.agility.AgilityWalk
import io.luna.game.model.Position
import io.luna.game.model.mob.Player

/**
 * The Yanille Agility Dungeon's obstacles: a ledge, a pipe, monkey bars and two piles of rubble linking its rooms.
 * Each can be used either way except the rubble, which has a pile for each way. Falls from the ledge and the monkey
 * bars drop into the poison spider cave, which lies 64 tiles north of them in the map, so a fall lands straight
 * below.
 *
 * @author TheLining
 */
object YanilleDungeon {

    /**
     * The obstacles' objects.
     */
    const val LEDGE = 2303
    const val PIPE = 2290
    const val MONKEY_BARS = 2321
    const val RUBBLE_UP = 2317
    const val RUBBLE_DOWN = 2318

    /**
     * HUMAN_PIPESQUEEZE and HUMAN_PIPEUNSQUEEZE: into the pipe and out of its far end.
     */
    const val PIPE_SQUEEZE = 746
    const val PIPE_UNSQUEEZE = 748

    /**
     * HUMAN_CRAWLING, the pose through the pipe.
     */
    const val PIPE_CRAWL = 844

    /**
     * HUMAN_MONKEYBARS_ON and HUMAN_MONKEYBARS_OFF: up onto the bars and the drop off them.
     */
    const val BARS_ON = 742
    const val BARS_OFF = 743

    /**
     * HUMAN_SIDESTEP_FALLL off the ledge and HUMAN_FALLING off the bars.
     */
    const val LEDGE_FALL = 761
    const val BARS_FALL = 766

    /**
     * HUMAN_FALLING_4, the pose held after either fall until the player lands.
     */
    const val FALLING = 768

    /**
     * The ledge's ends. It runs along x 2580 with the wall on its east side.
     */
    val LEDGE_NORTH = Position(2580, 9520)
    val LEDGE_SOUTH = Position(2580, 9512)

    /**
     * The pipe's ends: the east end is in the druids' cavern, the west end in the chest room.
     */
    val PIPE_EAST = Position(2578, 9506)
    val PIPE_WEST = Position(2572, 9506)

    /**
     * The rows of floor beyond each end of the monkey bars, which hang over x 2598–2599 from y 9489 to 9494.
     */
    const val BARS_NORTH = 9495
    const val BARS_SOUTH = 9488

    /**
     * Where each pile of rubble leads: up into Salarin's corridor, north of its pile, or down into the cave below,
     * south of the pile that climbs back up.
     */
    val RUBBLE_TOP = Position(2615, 9506)
    val RUBBLE_BOTTOM = Position(2616, 9571)

    /**
     * How far north of the upper level the spider cave lies.
     */
    private const val LOWER_LEVEL = 64

    /**
     * The success chances' low and high ends: about 71% on the ledge at 40, never failing from 66, and about 80% on
     * the bars at 57, never failing from 75.
     */
    private const val LEDGE_LOW = 65
    private const val LEDGE_HIGH = 355
    private const val BARS_LOW = 45
    private const val BARS_HIGH = 325

    private const val LEDGE_START_MESSAGE = "You put your foot on the ledge and try to edge across..."

    /**
     * Whether [position] is a tile the climb down the rubble starts from: beside the pile, in the corridor.
     */
    fun besideRubbleDown(position: Position) = position.z == 0 &&
        (position.x == 2614 && position.y in 9504..9505 || position.x in 2615..2616 && position.y in setOf(9503, 9506))

    /**
     * Whether this player slips on the ledge.
     */
    fun Player.slipsOnLedge() = slips(LEDGE_LOW, LEDGE_HIGH)

    /**
     * Whether this player loses their grip on the monkey bars.
     */
    fun Player.slipsOnBars() = slips(BARS_LOW, BARS_HIGH)

    private fun Player.slips(low: Int, high: Int) =
        rand().nextDouble() >= Skills.successRate(low, high, minOf(agility.level, 99))

    /**
     * Edges across the ledge from the end this player stands on, side-stepping with their back to the wall.
     */
    fun Player.crossLedge(): AgilityAction {
        val south = position == LEDGE_NORTH
        return AgilityAction(this, listOf(AgilityStep(if (south) LEDGE_SOUTH else LEDGE_NORTH, 8,
            walkingAnimation = ledgeWalk(south))), LEDGE_START_MESSAGE,
            endMessage = "You skillfully edge across the gap.")
    }

    /**
     * Slips halfway along the ledge and falls into the cave below.
     */
    fun Player.fallFromLedge(): AgilityAction {
        val middle = Position(2580, 9516)
        return fall(listOf(AgilityStep(middle, 4, walkingAnimation = ledgeWalk(position == LEDGE_NORTH))),
            LEDGE_FALL, middle, LEDGE_START_MESSAGE)
    }

    /**
     * The side-step that keeps the wall behind a player going [south]: the player faces west, so going south is a
     * step to the left.
     */
    private fun ledgeWalk(south: Boolean) = if (south) AgilityWalk.SIDESTEP_LEFT.walk else AgilityWalk.SIDESTEP.walk

    /**
     * Squeezes into the pipe from the end this player stands on, crawls through and comes out of its far end on the
     * last tile. The crawl pose is set while the player squeezes in: the client keeps the pose a player has when a
     * forced move starts.
     */
    fun Player.squeezeThroughPipe(): AgilityAction {
        val west = position == PIPE_EAST
        val end = if (west) PIPE_WEST else PIPE_EAST
        val mouth = end.translate(if (west) 1 else -1, 0)
        return AgilityAction(this, listOf(
            AgilityStep(position, 1, walkingAnimation = PIPE_CRAWL, animation = PIPE_SQUEEZE, jump = true),
            AgilityStep(mouth, 4),
            AgilityStep(end, 1, animation = PIPE_UNSQUEEZE)), null)
    }

    /**
     * Turns to the monkey bars, reaches up and swings along all six of them, dropping onto the far end.
     */
    fun Player.swingAcrossBars(): AgilityAction {
        val end = Position(x, if (y == BARS_NORTH) BARS_SOUTH + 1 else BARS_NORTH - 1)
        return AgilityAction(this, listOf(ready(), mountBars(),
            AgilityStep(end, 6, walkingAnimation = AgilityWalk.MONKEY_BARS.walk),
            AgilityStep(end, 1, animation = BARS_OFF, jump = true)), null)
    }

    /**
     * Loses grip halfway along the monkey bars and falls into the cave below.
     */
    fun Player.fallFromBars(): AgilityAction {
        val over = position.translate(0, if (y == BARS_NORTH) -3 else 3)
        return fall(listOf(ready(), mountBars(),
            AgilityStep(over, 3, walkingAnimation = AgilityWalk.MONKEY_BARS.walk)), BARS_FALL, over, null)
    }

    /**
     * The tick a player stands facing the bars before reaching up: footage has the reach a tick after the turn.
     */
    private fun Player.ready() = AgilityStep(position, 1, jump = true)

    /**
     * Reaching up to the first bar, in place.
     */
    private fun Player.mountBars() =
        AgilityStep(position, 2, walkingAnimation = AgilityWalk.MONKEY_BARS.walk, animation = BARS_ON, jump = true)

    /**
     * Climbs a pile of rubble: the player stands at it and moves to the other cave two ticks later.
     */
    fun Player.climbRubble(up: Boolean) = AgilityAction(this,
        listOf(AgilityStep(if (up) RUBBLE_TOP else RUBBLE_BOTTOM, 2, jump = true)), null,
        endMessage = if (up) "You climb up the pile of rubble..." else "You climb down the pile of rubble...")

    /**
     * What a drop into the spider cave deals: a fifth of the player's Hitpoints level, at least 1.
     */
    fun fallDamage(plr: Player) = maxOf(1, plr.hitpoints.staticLevel / 5)

    /**
     * A fall from an obstacle: [steps] on it, then [pose] for two ticks before the player drops into the cave straight
     * below [from] and takes [fallDamage]. No experience.
     */
    private fun Player.fall(steps: List<AgilityStep>, pose: Int, from: Position, message: String?) =
        AgilityAction(this, steps + AgilityStep(from.translate(0, LOWER_LEVEL), 2, walkingAnimation = FALLING,
            animation = pose, jump = true), message).also { it.onSuccess = { damage(fallDamage(this)) } }
}
