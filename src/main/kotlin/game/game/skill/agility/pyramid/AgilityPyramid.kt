package game.skill.agility.pyramid

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import api.predef.ext.*
import game.skill.agility.AgilityAction
import game.skill.agility.AgilityObstacle
import game.skill.agility.AgilityObstacle.*
import game.skill.agility.AgilityStep
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.varp.Varp

/**
 * The Agility Pyramid north-east of Sophanem: five levels of obstacles round a pyramid, the sliding and rolling
 * blocks that knock players a level down, and the gold top Simon Templeton buys.
 *
 * Levels 1 to 3 are floors 1 to 3 of the pyramid. Levels 4 and 5 are floors 2 and 3 of a copy of it further north,
 * which [COPY_DX] and [COPY_DY] move to, since the map has no fifth floor.
 *
 * @author TheLining
 */
object AgilityPyramid {

    /**
     * Simon Templeton, who buys the tops, and the two sliding blocks: the north one on level 3, the east one on level 1.
     */
    const val SIMON = 3123
    const val BLOCK_NORTH = 3124
    const val BLOCK_EAST = 3125

    /**
     * The gold top, what Simon pays for each, and coins.
     */
    const val PYRAMID_TOP = 6970
    const val TOP_PRICE = 1000
    const val COINS = 995

    /**
     * The obstacles.
     */
    const val ROCK_SLIDE = 10852
    const val TOP_ROCKS = 10851
    val DOORWAYS = listOf(10855, 10856)
    const val LOW_WALL = 10865
    val LEDGES = listOf(10860, 10886, 10887, 10888, 10889)
    val PLANKS = listOf(10867, 10868)
    val CROSS_GAPS = listOf(10861, 10862, 10863, 10864, 10882, 10883, 10884, 10885)
    const val JUMP_GAP = 10859

    /**
     * The pyramid's own animations, and the walks and climbs it shares with other courses.
     */
    const val LEDGE_ON = 3057
    const val LEDGE_WALK = 3060
    const val LEDGE_OFF = 3058
    const val LEDGE_FALL = 3056
    const val LEDGE_FALL_BACK = 3055
    const val GAP_HANG_FALL = 3062
    const val GAP_HANG_FALL_BACK = 3061
    const val TOP_CLIMB = 3063
    const val TILT_FALL = 3064
    const val BLOCK_PUSH_OUTER = 3065
    const val BLOCK_PUSH_INNER = 3066
    const val GAP_JUMP_ANIMATION = 3067
    const val GAP_JUMP_FALL = 3068
    const val PLANK_FALL = 3069
    const val LOW_WALL_CLIMB = 840
    const val LOW_WALL_SLIP = 1106
    const val BALANCE_WALK = 762
    const val GAP_HANG_WALK = 756
    const val CLIMB_WALK = 737

    /**
     * Where the copy that holds levels 4 and 5 sits, from the pyramid.
     */
    const val COPY_DX = -320
    const val COPY_DY = 1856

    /**
     * Where the doorway in the top block lets out: the sand in front of the doorway at the base.
     */
    val EXIT = Position(3364, 2829)

    /**
     * The varp holding the pyramid's state, drawn by the client: the level a player is on (which hides the sliding
     * blocks' stand-ins on that level), each rolling block's roll, whether the top is gone, and Simon's stages.
     */
    private const val VARP = 640
    private const val TILT_SHIFT = 3
    private const val TOP_BIT = 1 shl 9
    private const val SIMON_NAMED_BIT = 1 shl 10
    private const val SIMON_JOB_BIT = 1 shl 11
    private const val SIMON_JOB_DONE_BIT = 1 shl 12

    /**
     * Whether this player has taken the top since last leaving through the doorway.
     */
    var Player.pyramidTopTaken by Attr.boolean().persist("agility_pyramid_top")

    /**
     * How far this player has got with Simon: 0 before meeting him, 1 once he has asked for the tops, 2 once they have
     * sold him one.
     */
    var Player.simonStage by Attr.int().persist("simon_templeton_stage")

    /**
     * The rolling block (1 to 5) rolling under this player, or 0.
     */
    var Player.pyramidTilt by Attr.int()

    /**
     * The varp value last sent to this player.
     */
    private var Player.pyramidVarp by Attr.int()

    /**
     * The tile this player stood on last tick, while on the pyramid.
     */
    var Player.pyramidLastTile by Attr.nullableObj(Position::class)

    /**
     * Returns the pyramid level at [position], 1 to 5, or 0 off the pyramid.
     */
    fun levelAt(position: Position): Int = when {
        position.z in 1..3 && position.x in 3354..3375 && position.y in 2831..2852 -> position.z
        position.z in 2..3 && position.x in 3040..3049 && position.y in 4693..4702 -> position.z + 2
        else -> 0
    }

    /**
     * Sends this player's pyramid varp when it has changed.
     */
    fun sync(plr: Player) {
        var value = levelAt(plr.position)
        if (plr.pyramidTilt in 1..5) value = value or (1 shl (TILT_SHIFT + plr.pyramidTilt))
        if (plr.pyramidTopTaken) value = value or TOP_BIT
        if (plr.simonStage >= 1) value = value or SIMON_NAMED_BIT or SIMON_JOB_BIT
        if (plr.simonStage >= 2) value = value or SIMON_JOB_DONE_BIT
        if (value != plr.pyramidVarp) {
            plr.pyramidVarp = value
            plr.sendVarp(Varp(VARP, value))
        }
    }

    /**
     * Whether this player's pyramid varp needs a look this tick: they're on the pyramid, or were last tick.
     */
    fun watched(plr: Player) = levelAt(plr.position) != 0 || plr.pyramidVarp and 0xF != 0

    /**
     * What can knock a player off, the chance at level 30 and the level it stops at, and the damage a fall does.
     */
    enum class Hazard(val chanceAt30: Double, val safeLevel: Int, val damage: Int) {
        JUMP_GAP(0.25, 75, 8),
        CROSS_GAP(0.20, 69, 8),
        PLANK(0.15, 69, 10),
        LEDGE(0.12, 69, 10),
        ROLLING_BLOCK(0.08, 69, 6),
        LOW_WALL(0.03, 58, 3)
    }

    /**
     * The damage of a fall from going the wrong way, and of a sliding block's push.
     */
    const val BACKWARDS_DAMAGE = 1
    const val PUSH_DAMAGE = 8

    /**
     * Whether this player slips on [hazard] on [level]. The chance falls away from level 30 to [Hazard.safeLevel] and is
     * a tenth higher for each level up the pyramid.
     */
    fun Player.slips(hazard: Hazard, level: Int): Boolean {
        val chance = hazard.chanceAt30 * (hazard.safeLevel - agility.level) / (hazard.safeLevel - 30.0) *
            (1 + 0.1 * (level - 1))
        return rand().nextDouble() < chance.coerceIn(0.0, 1.0)
    }

    /**
     * An obstacle crossed [length] tiles in [direction] from [starts] (one tile, or one for each lane of the
     * walkway), on [level]. [locs] are where its objects stand, and a fall from it lands on [landing], a level down.
     */
    class Crossing(val obstacle: AgilityObstacle, val hazard: Hazard, val level: Int, val locs: List<Position>,
                   val starts: List<Position>, val direction: Direction, val length: Int, val landing: Position) {

        /**
         * The tiles a crossing ends on, one for each start.
         */
        val ends = starts.map { it.translate(length, direction) }

        /**
         * Whether [position] is past the obstacle, where taking it goes backwards.
         */
        fun behind(position: Position): Boolean {
            val start = starts.first()
            val along = (position.x - start.x) * direction.translateX + (position.y - start.y) * direction.translateY
            return along >= length
        }

        /**
         * The tile [position] takes this obstacle from: its start, or its end when going backwards, in the nearest
         * lane.
         */
        fun startFor(position: Position): Position =
            (if (behind(position)) ends else starts).minByOrNull { it.computeLongestDistance(position) }!!

        /**
         * The tile a crossing from [from] ends on.
         */
        fun endFor(from: Position): Position = from.translate(length, if (behind(from)) direction.opposite() else
            direction)

        /**
         * A tile part way along a crossing from [from], [tiles] in.
         */
        fun along(from: Position, tiles: Int): Position = from.translate(tiles,
            if (behind(from)) direction.opposite() else direction)
    }

    private fun tile(x: Int, y: Int, z: Int) = Position(x, y, z)

    /**
     * Every crossing, level by level and in the order the course takes them (clockwise from each level's stairs).
     */
    val CROSSINGS = listOf(
        // Level 1.
        Crossing(PYRAMID_LOW_WALL, Hazard.LOW_WALL, 1, listOf(tile(3354, 2849, 1)),
            listOf(tile(3354, 2848, 1), tile(3355, 2848, 1)), Direction.NORTH, 2, tile(3352, 2846, 0)),
        Crossing(PYRAMID_LEDGE, Hazard.LEDGE, 1, listOf(tile(3364, 2851, 1), tile(3366, 2851, 1)),
            listOf(tile(3363, 2851, 1)), Direction.EAST, 5, tile(3365, 2853, 0)),
        Crossing(PYRAMID_PLANK, Hazard.PLANK, 1, listOf(tile(3375, 2845, 1), tile(3375, 2840, 1)),
            listOf(tile(3375, 2845, 1)), Direction.SOUTH, 5, tile(3377, 2843, 0)),
        Crossing(PYRAMID_CROSS_GAP, Hazard.CROSS_GAP, 1,
            listOf(tile(3372, 2832, 1), tile(3370, 2831, 1), tile(3368, 2831, 1), tile(3367, 2832, 1)),
            listOf(tile(3372, 2832, 1)), Direction.WEST, 5, tile(3370, 2830, 0)),
        Crossing(PYRAMID_LEDGE, Hazard.LEDGE, 1, listOf(tile(3362, 2831, 1), tile(3360, 2831, 1)),
            listOf(tile(3364, 2832, 1)), Direction.WEST, 5, tile(3362, 2830, 0)),

        // Level 2.
        Crossing(PYRAMID_CROSS_GAP, Hazard.CROSS_GAP, 2,
            listOf(tile(3357, 2836, 2), tile(3356, 2837, 2), tile(3356, 2839, 2), tile(3357, 2841, 2)),
            listOf(tile(3357, 2836, 2)), Direction.NORTH, 5, tile(3355, 2839, 1)),
        Crossing(PYRAMID_JUMP_GAP, Hazard.JUMP_GAP, 2, listOf(tile(3356, 2847, 2)),
            listOf(tile(3356, 2846, 2), tile(3357, 2846, 2)), Direction.NORTH, 3, tile(3354, 2847, 1)),
        Crossing(PYRAMID_CROSS_GAP, Hazard.CROSS_GAP, 2,
            listOf(tile(3359, 2849, 2), tile(3360, 2849, 2), tile(3362, 2849, 2), tile(3364, 2849, 2)),
            listOf(tile(3359, 2849, 2)), Direction.EAST, 5, tile(3362, 2851, 1)),
        Crossing(PYRAMID_LEDGE, Hazard.LEDGE, 2, listOf(tile(3372, 2837, 2), tile(3372, 2839, 2)),
            listOf(tile(3372, 2841, 2)), Direction.SOUTH, 5, tile(3374, 2839, 1)),
        Crossing(PYRAMID_LOW_WALL, Hazard.LOW_WALL, 2, listOf(tile(3370, 2833, 2)),
            listOf(tile(3371, 2833, 2), tile(3371, 2834, 2)), Direction.WEST, 2, tile(3372, 2832, 1)),
        Crossing(PYRAMID_JUMP_GAP, Hazard.JUMP_GAP, 2, listOf(tile(3364, 2833, 2)),
            listOf(tile(3366, 2833, 2), tile(3366, 2834, 2)), Direction.WEST, 3, tile(3364, 2831, 1)),

        // Level 3.
        Crossing(PYRAMID_LOW_WALL, Hazard.LOW_WALL, 3, listOf(tile(3358, 2839, 3)),
            listOf(tile(3358, 2838, 3), tile(3359, 2838, 3)), Direction.NORTH, 2, tile(3356, 2841, 2)),
        Crossing(PYRAMID_LEDGE, Hazard.LEDGE, 3, listOf(tile(3358, 2843, 3), tile(3358, 2845, 3)),
            listOf(tile(3359, 2842, 3)), Direction.NORTH, 5, tile(3357, 2845, 2)),
        Crossing(PYRAMID_JUMP_GAP, Hazard.JUMP_GAP, 3, listOf(tile(3370, 2841, 3)),
            listOf(tile(3370, 2843, 3), tile(3371, 2843, 3)), Direction.SOUTH, 3, tile(3373, 2841, 2)),
        Crossing(PYRAMID_PLANK, Hazard.PLANK, 3, listOf(tile(3370, 2835, 3), tile(3365, 2835, 3)),
            listOf(tile(3370, 2835, 3)), Direction.WEST, 5, tile(3367, 2833, 2)),

        // Level 4, on the copy. Falls land on level 3 of the pyramid itself.
        Crossing(PYRAMID_JUMP_GAP, Hazard.JUMP_GAP, 4, listOf(tile(3040, 4697, 2)),
            listOf(tile(3040, 4696, 2), tile(3041, 4696, 2)), Direction.NORTH, 3, tile(3358, 2841, 3)),
        Crossing(PYRAMID_JUMP_GAP, Hazard.JUMP_GAP, 4, listOf(tile(3048, 4695, 2)),
            listOf(tile(3048, 4697, 2), tile(3049, 4697, 2)), Direction.SOUTH, 3, tile(3371, 2839, 3)),
        Crossing(PYRAMID_LOW_WALL, Hazard.LOW_WALL, 4, listOf(tile(3047, 4693, 2)),
            listOf(tile(3048, 4693, 2), tile(3048, 4694, 2)), Direction.WEST, 2, tile(3365, 2835, 3)),

        // Level 5.
        Crossing(PYRAMID_JUMP_GAP, Hazard.JUMP_GAP, 5, listOf(tile(3046, 4697, 3)),
            listOf(tile(3046, 4699, 3), tile(3047, 4699, 3)), Direction.SOUTH, 3, tile(3049, 4697, 2))
    )

    /**
     * The tiles each kind of crossing starts from, either way.
     */
    private val STARTS: Map<AgilityObstacle, Set<Position>> = CROSSINGS.groupBy { it.obstacle }
        .mapValues { (_, crossings) -> crossings.flatMap { it.starts + it.ends }.toSet() }

    /**
     * Whether a crossing of [obstacle] starts from [position], either way.
     */
    fun startsFrom(obstacle: AgilityObstacle, position: Position) = STARTS[obstacle]?.contains(position) == true

    /**
     * The crossing an object at [position] belongs to.
     */
    fun crossingAt(position: Position): Crossing? = CROSSINGS.firstOrNull { position in it.locs }

    /**
     * The crossing of [obstacle] that starts, either way, from [position].
     */
    fun crossingFrom(obstacle: AgilityObstacle, position: Position): Crossing? =
        CROSSINGS.firstOrNull { it.obstacle == obstacle && (position in it.starts || position in it.ends) }

    /**
     * A rolling block: [tilt] is its varbit's number, [tiles] its square of walkway on [level]. The course crosses it
     * in [direction]; a player who keeps their footing is thrown on to the tile past it, and one who doesn't falls to
     * [landing].
     */
    class RollingBlock(val tilt: Int, val level: Int, val tiles: Set<Position>, val direction: Direction,
                       val landing: Position) {

        /**
         * Whether [from], the tile a player stepped on from, is on the near side, so they're going the right way.
         */
        fun forwards(from: Position): Boolean =
            along(from) < tiles.minOf { along(it) }

        private fun along(position: Position) =
            position.x * direction.translateX + position.y * direction.translateY

        /**
         * The tile past the block, in the lane of [position], where a player is thrown.
         */
        fun past(position: Position): Position =
            position.translate(tiles.maxOf { along(it) } + 1 - along(position), direction)
    }

    private fun square(x: Int, y: Int, z: Int) =
        setOf(tile(x, y, z), tile(x + 1, y, z), tile(x, y + 1, z), tile(x + 1, y + 1, z))

    /**
     * The five rolling blocks.
     */
    val ROLLING_BLOCKS = listOf(
        RollingBlock(1, 1, square(3354, 2841, 1), Direction.NORTH, tile(3352, 2846, 0)),
        RollingBlock(2, 2, square(3368, 2849, 2), Direction.EAST, tile(3368, 2852, 1)),
        RollingBlock(3, 1, square(3374, 2835, 1), Direction.SOUTH, tile(3377, 2835, 0)),
        RollingBlock(4, 4, square(3048, 4699, 2), Direction.SOUTH, tile(3371, 2844, 3)),
        RollingBlock(5, 5, square(3044, 4699, 3), Direction.EAST, tile(3044, 4702, 2))
    )

    /**
     * A sliding block: NPC [npcId] stands in the core at [home] and slides [out] across [lane] and back every
     * [CYCLE] ticks, [phase] ticks out of step with the other. Whoever it pushes off lands on [landing], a level down.
     */
    class SlidingBlock(val npcId: Int, val home: Position, val out: Direction, val phase: Int,
                       val lane: Set<Position>, val landing: Position) {
        var npc: Npc? = null
    }

    /**
     * Ticks in a sliding block's cycle, and the ticks at its start that it fills its lane: two ticks sliding out, one
     * out, two sliding back.
     */
    const val CYCLE = 15
    const val BLOCKING_TICKS = 5

    val SLIDING_BLOCKS = listOf(
        SlidingBlock(BLOCK_EAST, tile(3372, 2847, 1), Direction.EAST, 0, square(3374, 2847, 1), tile(3377, 2847, 0)),
        SlidingBlock(BLOCK_NORTH, tile(3366, 2845, 3), Direction.NORTH, 7, square(3366, 2847, 3), tile(3366, 2850, 2))
    )

    init {
        // A start tile must lead to one crossing, or a click couldn't tell which it was.
        for (crossing in CROSSINGS) {
            for (start in crossing.starts + crossing.ends) {
                check(CROSSINGS.count { it.obstacle == crossing.obstacle && (start in it.starts || start in it.ends) } == 1)
            }
        }
    }

    /**
     * A fall from an obstacle: [steps] on it, then [pose] for [poseTicks] ticks before the player drops to
     * [landing], a level down, and takes [hit] two ticks later. No experience.
     */
    fun Player.fall(steps: List<AgilityStep>, pose: Int, poseTicks: Int, landing: Position, hit: Int,
                    message: String? = null) =
        AgilityAction(this, steps + listOf(AgilityStep(landing, poseTicks, animation = pose, jump = true),
            AgilityStep(landing, 2, jump = true)), message).also { it.onSuccess = { damage(hit) } }

    /**
     * Rolls block [block] under this player for a few ticks.
     */
    fun Player.tilt(block: RollingBlock) {
        pyramidTilt = block.tilt
        sync(this)
        world.scheduleOnce(3) {
            if (pyramidTilt == block.tilt) {
                pyramidTilt = 0
                sync(this)
            }
        }
    }
}
