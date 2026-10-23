package game.content.dwarfMulticannon

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import io.luna.game.model.Position
import io.luna.game.model.collision.CollisionFlag
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.ObjectDirection
import io.luna.game.model.path.route.LineOfSight

/**
 * Constants, saved data and set-up rules for the Dwarf multicannon.
 *
 * @author TheLining
 */
object DwarfMulticannon {

    /**
     * The cannonball item ID.
     */
    const val CANNONBALL = 2

    /**
     * The ammo mould item ID.
     */
    const val AMMO_MOULD = 4

    /**
     * The instruction manual item ID.
     */
    const val INSTRUCTION_MANUAL = 5

    /**
     * Nulodion, the Dwarven armoury engineer.
     */
    const val NULODION = 209

    /**
     * The most cannonballs a cannon holds.
     */
    const val MAX_AMMO = 30

    /**
     * The ticks a cannon stays out before it decays. Adding a part starts it again.
     */
    const val DECAY_TICKS = 2500

    /**
     * The south-west tile of the player's cannon, or `null` if they have none out. It is kept after the cannon
     * decays or is destroyed, so Nulodion knows it was lost.
     */
    var Player.cannonPosition: Position? by Attr.nullableObj(Position::class).persist("cannon_position")

    /**
     * The stage the player's cannon was built to, or `null` if they have none out.
     */
    var Player.cannonStage: CannonStage? by Attr.nullableObj(CannonStage::class).persist("cannon_stage")

    /**
     * A box on a range of height levels.
     */
    private class Zone(val swX: Int, val swY: Int, val neX: Int, val neY: Int, val levels: IntRange = 0..0) {
        fun contains(pos: Position) = pos.z in levels && pos.x in swX..neX && pos.y in swY..neY
    }

    /**
     * Banks.
     */
    private val BANKS = listOf(
        Zone(3253, 3425, 3253, 3427), // Varrock east
        Zone(3250, 3416, 3257, 3424, 0..1),
        Zone(3180, 3433, 3190, 3447, 0..1), // Varrock west
        Zone(3265, 3161, 3272, 3173), // Al Kharid
        Zone(3088, 3240, 3097, 3246), // Draynor
        Zone(3011, 3357, 3014, 3358), // Falador east
        Zone(3009, 3353, 3021, 3356),
        Zone(2945, 3366, 2947, 3373), // Falador west
        Zone(2948, 3366, 2949, 3369),
        Zone(2946, 3362, 2948, 3365),
        Zone(2945, 3359, 2948, 3361),
        Zone(2945, 3362, 2949, 3369, 1..1),
        Zone(3091, 3488, 3098, 3499), // Edgeville
        Zone(2721, 3490, 2730, 3497, 0..1), // Seers' Village
        Zone(2724, 3487, 2727, 3489),
        Zone(2806, 3438, 2812, 3445), // Catherby
        Zone(2609, 3088, 2616, 3097), // Yanille
        Zone(2612, 3335, 2614, 3335), // Ardougne west
        Zone(2619, 3335, 2621, 3335),
        Zone(2612, 3330, 2621, 3334),
        Zone(2649, 3280, 2658, 3287, 0..2), // Ardougne east
        Zone(2843, 2952, 2845, 2955), // Shilo Village
        Zone(2846, 2953, 2858, 2955),
        Zone(2859, 2952, 2861, 2955),
        Zone(2850, 2951, 2854, 2957),
        Zone(2843, 2951, 2861, 2957, 1..1),
        Zone(2443, 3415, 2448, 3434, 1..1), // Gnome Stronghold
        Zone(2440, 3416, 2453, 3496, 1..1),
        Zone(2529, 4711, 2546, 4723), // Mage Arena
        Zone(3308, 3119, 3309, 3121), // Shantay Pass
        Zone(2586, 3418, 2587, 3422), // Fishing Guild
        Zone(3148, 9573, 3155, 9582), // Zanaris
        Zone(3380, 3267, 3384, 3271) // Duel Arena
    )

    /**
     * The Duel Arena's fight zones, the Castle Wars game and its waiting rooms.
     */
    private val MINIGAMES = listOf(
        Zone(3333, 3244, 3357, 3258), // Duel Arena
        Zone(3364, 3225, 3388, 3239),
        Zone(3333, 3206, 3357, 3220),
        Zone(3364, 3244, 3388, 3258),
        Zone(3333, 3225, 3357, 3239),
        Zone(3364, 3206, 3388, 3220),
        Zone(2368, 3072, 2431, 3135, 0..3), // Castle Wars
        Zone(2368, 9472, 2431, 9535)
    )

    /**
     * Ice Mountain, near the Black Guard, and the Dwarven Mine.
     */
    private val DWARF_AREAS = listOf(
        Zone(2979, 3417, 3071, 3455),
        Zone(3008, 3456, 3071, 3519),
        Zone(2944, 9804, 3071, 9855),
        Zone(2999, 9792, 3071, 9804),
        Zone(3008, 9728, 3071, 9855)
    )

    /**
     * The Fight Arena.
     */
    private val FIGHT_ARENA = Zone(2581, 3151, 2606, 3171)

    /**
     * The dungeon under the lighthouse.
     */
    private val LIGHTHOUSE_DUNGEON = Zone(2496, 4608, 2559, 4671)

    /**
     * The tiles, relative to the player, that the player may step to before setting up.
     */
    private val SETUP_STEPS = listOf(
        -2 to 2, -1 to 2, 0 to 2, 1 to 2, 2 to 2,
        2 to -2, 1 to -2, 0 to -2, -1 to -2, -2 to -2,
        -2 to -1, -2 to 0, -2 to 1,
        2 to 1, 2 to 0, 2 to -1
    )

    /**
     * Returns the message for why a cannon can't be set up around [pos], or `null` if it can.
     */
    fun checkSetUp(pos: Position): String? {
        return when {
            DWARF_AREAS.any { it.contains(pos) } -> "The dwarves won't be happy if you set up a cannon here."
            BANKS.any { it.contains(pos) } || MINIGAMES.any { it.contains(pos) } -> "You can't set up a cannon here."
            FIGHT_ARENA.contains(pos) -> "It is not permitted to set up a cannon inside the Fight Arena."
            isOccupied(pos.translate(-1, -1)) -> "There isn't enough space to set up here."
            LIGHTHOUSE_DUNGEON.contains(pos) -> "The air is too dank for you to set up a cannon here."
            !hasSpace(pos) -> "There isn't enough space to set up here."
            else -> null
        }
    }

    /**
     * Picks a random tile 2 away from [pos] that can be walked to and back from in a straight line, or `null` if
     * none was found.
     */
    fun findSetUpStep(pos: Position): Position? {
        repeat(50) {
            val (x, y) = SETUP_STEPS.random()
            val step = pos.translate(x, y)
            if (lineOfWalk(step, pos) && lineOfWalk(pos, step)) {
                return step
            }
        }
        return null
    }

    /**
     * Determines if an object with options covers [pos]. A cannon can't be set up with its south-west tile there.
     */
    fun isOccupied(pos: Position): Boolean {
        return world.locator.findObjects(pos, 8) {
            val def = it.def()
            if (def == null || !def.isInteractive) {
                return@findObjects false
            }
            val turned = it.direction == ObjectDirection.NORTH || it.direction == ObjectDirection.SOUTH
            val width = if (turned) it.sizeY() else it.sizeX()
            val length = if (turned) it.sizeX() else it.sizeY()
            pos.x in it.position.x until it.position.x + width && pos.y in it.position.y until it.position.y + length
        }.isNotEmpty()
    }

    /**
     * Determines if the 3x3 area around [centre] is clear, and can be walked across in every direction.
     */
    private fun hasSpace(centre: Position): Boolean {
        val view = world.collisionManager.view(false)
        for (x in -1..1) {
            for (y in -1..1) {
                if (view.get(centre.x + x, centre.y + y, centre.z) and (CollisionFlag.LOC or CollisionFlag.FLOOR_BLOCKED) != 0) {
                    return false
                }
            }
        }
        return lineOfWalk(centre.translate(0, 1), centre.translate(0, -1)) &&
            lineOfWalk(centre.translate(1, 0), centre.translate(-1, 0)) &&
            lineOfWalk(centre.translate(1, 1), centre.translate(-1, -1)) &&
            lineOfWalk(centre.translate(-1, 1), centre.translate(1, -1))
    }

    /**
     * Determines if nothing that stops walking crosses the straight line from [from] to [to].
     */
    private fun lineOfWalk(from: Position, to: Position) =
        LineOfSight.hasLineOfWalk(world.collisionManager.view(false), from.z, from.x, from.y, to.x, to.y, 1, 1, 1, 1, 0)
}
