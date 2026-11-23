package game.content.magicGuild

import api.predef.*
import api.predef.ext.*
import game.obj.doors.DoorSide
import game.obj.doors.DoorType
import game.obj.doors.Doors
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.movement.NavigationResult
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection
import io.luna.game.model.`object`.ObjectType
import java.util.concurrent.CompletableFuture

/**
 * The two double doors of the Magic Guild in Yanille. Only players with a Magic level of [REQUIRED_LEVEL] or more, boosts
 * included, may enter, while anyone may leave.
 *
 * The doors are not in the door files. Both leaves swing open for one player at a time, and an invisible wall stands on
 * each leaf's closed tile until they close, so that nobody follows the player in without their own level check.
 *
 * @author TheLining
 */
object MagicGuild {

    /**
     * The id of the left leaf of each door.
     */
    const val LEFT_DOOR = 1600

    /**
     * The id of the right leaf of each door.
     */
    const val RIGHT_DOOR = 1601

    /**
     * The Magic level needed to enter.
     */
    const val REQUIRED_LEVEL = 66

    /**
     * Wizard Distentor, the head of the guild, who turns away players below [REQUIRED_LEVEL].
     */
    private const val DISTENTOR = 462

    /**
     * The id of the invisible wall that stands in the doorway while the door is open.
     */
    private const val BLOCKER = 83

    /**
     * The ticks that a door stays open for.
     */
    private const val OPEN_TICKS = 3

    /**
     * The most ticks that a player is given to reach the door and for it to be free, before they are given up on.
     */
    private const val MAX_TICKS = 30

    private val leftType = DoorType(LEFT_DOOR, 1522, DoorSide.LEFT, null, null, OPEN_TICKS)
    private val rightType = DoorType(RIGHT_DOOR, 1523, DoorSide.RIGHT, null, null, OPEN_TICKS)

    /**
     * One of the guild's two doors.
     *
     * @param left The position of the closed left leaf.
     * @param right The position of the closed right leaf.
     * @param direction The direction that both closed leaves face.
     */
    private class Entrance(val left: Position, val right: Position, val direction: ObjectDirection) {

        /**
         * If the door is open, or about to be, for someone.
         */
        var busy = false
    }

    private val entrances = listOf(
        Entrance(Position(2584, 3088), Position(2584, 3087), ObjectDirection.EAST),
        Entrance(Position(2597, 3087), Position(2597, 3088), ObjectDirection.WEST))

    /**
     * Whether [plr]'s current Magic level, boosts and drains included, lets them in.
     */
    fun canEnter(plr: Player): Boolean = plr.skill(Skill.MAGIC).level >= REQUIRED_LEVEL

    /**
     * Takes [plr] through the door that [door] is a leaf of.
     */
    fun openDoor(plr: Player, door: GameObject) {
        val entrance = entrances.firstOrNull { it.left == door.position || it.right == door.position } ?: return
        if (plr.actions.first(Crossing::class.java) == null) {
            plr.submitAction(Crossing(plr, door, entrance))
        }
    }

    /**
     * Swings both leaves of [entrance] open and keeps the doorway shut to everyone else until they close again.
     * Everything is checked before anything is changed, so a door that cannot be opened is left as it was.
     *
     * @return `true` if the door is now open.
     */
    private fun open(plr: Player, entrance: Entrance): Boolean {
        if (entrance.busy) {
            return false
        }
        val leaves = listOf(Pair(entrance.left, leftType), Pair(entrance.right, rightType))
        val closed = leaves.map { (position, type) ->
            world.objects.findAll(position)
                .filter { it.id == type.closed && it.direction == entrance.direction }
                .findFirst().orElse(null) ?: return false
        }
        val swings = closed.mapIndexed { index, leaf -> Doors.doubleSwap(leaf, leaves[index].second, true) }
        val removed = closed.filter { world.removeObject(it) }
        if (removed.size != closed.size) {
            removed.forEach { world.addObject(it.id, it.position, it.objectType, it.direction) }
            return false
        }

        val placed = ArrayList<GameObject>(4)
        swings.forEach { placed += world.addObject(it.id, it.position, it.old.objectType, it.direction) }
        closed.forEach { placed += world.addObject(BLOCKER, it.position, ObjectType.STRAIGHT_WALL, it.direction) }
        entrance.busy = true
        plr.playSound(leftType.openSoundOrDefault)
        world.scheduleOnce(OPEN_TICKS) {
            try {
                placed.forEach { world.removeObject(it) }
                closed.forEach { world.addObject(it.id, it.position, it.objectType, it.direction) }
            } finally {
                entrance.busy = false
            }
        }
        return true
    }

    /**
     * Walks the player to the clicked leaf, checks their level if they are entering, and sends them through.
     * This is a weak action, so walking away cancels it.
     *
     * @author TheLining
     */
    private class Crossing(plr: Player, door: GameObject, private val entrance: Entrance) :
        Action<Player>(plr, ActionType.WEAK) {

        /**
         * If the player is going into the guild, as opposed to out of it.
         */
        private val entering = Doors.isEntering(door.position, entrance.direction, plr.position)

        /**
         * The tile inside the guild, next to the clicked leaf.
         */
        private val inside = door.position.translate(1, entrance.direction.toNormalDirection())

        /**
         * The tile that the player goes through the door from.
         */
        private val start = if (entering) door.position else inside

        /**
         * The tile that the player ends up on.
         */
        private val destination = if (entering) inside else door.position

        private var walking: CompletableFuture<NavigationResult>? = null
        private var ticks = 0

        override fun run(): Boolean {
            if (++ticks > MAX_TICKS) {
                mob.sendMessage("You can't get through the door right now.")
                return true
            }
            val pending = walking
            if (pending != null && !pending.isDone) {
                return false
            }
            if (mob.position != start) {
                if (pending != null) {
                    mob.sendMessage("You can't reach the door from here.")
                    return true
                }
                walking = mob.navigator.navigate(start, false)
                return false
            }
            if (entering && !canEnter(mob)) {
                mob.newDialogue().npc(DISTENTOR, "You need a magic level of $REQUIRED_LEVEL.",
                                      "The magical energy in here is unsafe for those",
                                      "below that level.").open()
                return true
            }
            if (entrance.busy) {
                return false
            }
            if (open(mob, entrance)) {
                mob.walking.addForcedStep(Direction.between(start, destination))
            }
            return true
        }
    }
}
