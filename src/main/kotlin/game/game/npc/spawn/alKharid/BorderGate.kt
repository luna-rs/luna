package game.npc.spawn.alKharid

import api.predef.*
import api.predef.ext.*
import game.obj.doors.DoorSide
import game.obj.doors.DoorType
import game.obj.doors.Doors
import game.player.Sound
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.World
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.movement.NavigationResult
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection
import io.luna.game.model.`object`.ObjectType
import java.util.concurrent.CompletableFuture

/**
 * The toll gate between Lumbridge and Al Kharid, and the four border guards that stand beside it.
 *
 * Using the gate, or talking to a guard, asks the player for the [TOLL]. A player that pays has the gate opened for them
 * and takes a single step through it. The gate belongs to this script alone and is not one of the doors in the door
 * files, but it swings like the leaves of a double door, so the placement of the open leaves comes from [Doors].
 *
 * While the gate is open, an invisible wall stands on each of the tiles that the closed leaves were on. That is what keeps
 * everyone but the paying player from walking through, because collision is per tile and not per player. The paying player
 * is moved by a forced step, which ignores collision, so they are not stopped by it.
 *
 * @author Hydrozoa
 */
object BorderGate {

    /**
     * The toll, in coins.
     */
    const val TOLL = 10

    /**
     * The id of the guards that stand on the Lumbridge side of the gate.
     */
    const val LUMBRIDGE_GUARD = 925

    /**
     * The id of the guards that stand on the Al Kharid side of the gate.
     */
    const val AL_KHARID_GUARD = 926

    /**
     * The id of the closed leaf of the gate on the southern row ([SOUTH_ROW]).
     */
    const val LEFT_LEAF = 2882

    /**
     * The id of the closed leaf of the gate on the northern row ([NORTH_ROW]).
     */
    const val RIGHT_LEAF = 2883

    /**
     * The id of the open leaf that [LEFT_LEAF] swings into.
     */
    private const val LEFT_LEAF_OPEN = 1562

    /**
     * The id of the open leaf that [RIGHT_LEAF] swings into.
     */
    private const val RIGHT_LEAF_OPEN = 1563

    /**
     * The id of the invisible wall that stands in the gateway while the gate is open.
     */
    private const val BLOCKER = 83

    /**
     * The id of the coins that the toll is paid in.
     */
    private const val COINS = 995

    /**
     * The x coordinate of the gate line. The closed leaves are walls on the western edge of this column, so the tiles
     * west of it are in Lumbridge and the tiles from it eastwards are in Al Kharid.
     */
    private const val GATE_X = 3268

    /**
     * The row of the southern leaf.
     */
    private const val SOUTH_ROW = 3227

    /**
     * The row of the northern leaf.
     */
    private const val NORTH_ROW = 3228

    /**
     * The ticks that the gate stays open for.
     */
    private const val OPEN_TICKS = 3

    /**
     * The most ticks that a player is given to reach the gate and for it to be free, before they are given up on.
     */
    private const val MAX_TICKS = 30

    /**
     * How far from the player a guard can be and still turn to answer them when they use the gate.
     */
    private const val GUARD_REACH = 4

    /**
     * One leaf of the gate.
     *
     * @param type The closed and open ids of the leaf, in the form [Doors] works with. It is never added to [Doors].
     * @param position The position of the leaf while it is closed.
     */
    private class Leaf(val type: DoorType, val position: Position)

    /**
     * Both leaves of the gate.
     */
    private val leaves = listOf(
        Leaf(DoorType(LEFT_LEAF, LEFT_LEAF_OPEN, DoorSide.LEFT, Sound.GRATE_OPEN, null, OPEN_TICKS),
             Position(GATE_X, SOUTH_ROW)),
        Leaf(DoorType(RIGHT_LEAF, RIGHT_LEAF_OPEN, DoorSide.RIGHT, Sound.GRATE_OPEN, null, OPEN_TICKS),
             Position(GATE_X, NORTH_ROW)))

    /**
     * If the gate is open, or about to be, for someone. Only one player goes through at a time.
     */
    private var busy = false

    /**
     * Determines if [plr] may pass without paying.
     */
    fun isFree(plr: Player): Boolean {
        // TODO@ Return true once the player has completed Prince Ali Rescue, which makes them a friend of Al Kharid.
        //  There is no quest system yet, so for now everybody pays.
        return false
    }

    /**
     * Determines if [position] is on the Lumbridge side of the gate, as opposed to the Al Kharid side.
     */
    fun isLumbridgeSide(position: Position): Boolean = position.x < GATE_X

    /**
     * Computes the row that a player at [y] goes through the gate on, which is the closest of the two rows that have a leaf.
     */
    fun rowFor(y: Int): Int = y.coerceIn(SOUTH_ROW, NORTH_ROW)

    /**
     * Computes the tile that a player on [row] must stand on to go through the gate, on the side [fromLumbridge].
     */
    fun startFor(fromLumbridge: Boolean, row: Int): Position = Position(if (fromLumbridge) GATE_X - 1 else GATE_X, row)

    /**
     * Starts the conversation for a player that used the gate. The guard on the player's side, if there is one close by,
     * turns to the player first.
     *
     * @param plr The player.
     */
    fun useGate(plr: Player) {
        val guard = if (isLumbridgeSide(plr.position)) LUMBRIDGE_GUARD else AL_KHARID_GUARD
        plr.world.locator.findNpcs(plr, GUARD_REACH, true) { it.id == guard }.firstOrNull()?.interact(plr)
        talk(plr, guard)
    }

    /**
     * Starts the conversation about the toll.
     *
     * @param plr The player.
     * @param guard The id of the guard that is speaking to them.
     */
    fun talk(plr: Player, guard: Int) {
        val dialogue = plr.newDialogue().player("Can I come through this gate?")
        if (isFree(plr)) {
            dialogue.npc(guard, "You may pass for free, you are a friend of Al-Kharid.")
                .then { cross(it, true) }
                .open()
            return
        }
        dialogue.npc(guard, "You must pay a toll of $TOLL gold coins to pass.")
            .options("No thank you, I'll walk around.", { decline(it, guard) },
                     "Who does my money go to?", { askWhere(it, guard) },
                     "Yes, ok.", { agree(it) })
            .open()
    }

    /**
     * Ends the conversation after the player turned the toll down.
     */
    private fun decline(plr: Player, guard: Int) {
        plr.newDialogue()
            .player("No thank you, I'll walk around.")
            .npc(guard, "Ok suit yourself.")
            .open()
    }

    /**
     * Answers the player's question about where the toll goes, which ends the conversation.
     */
    private fun askWhere(plr: Player, guard: Int) {
        plr.newDialogue()
            .player("Who does my money go to?")
            .npc(guard, "The money goes to the city of Al-Kharid.")
            .open()
    }

    /**
     * Handles the player agreeing to pay. They cross if they have the coins for it, otherwise they are told they do not.
     */
    private fun agree(plr: Player) {
        val dialogue = plr.newDialogue().player("Yes, ok.")
        if (plr.inventory.computeAmountForId(COINS) < TOLL) {
            dialogue.player("Oh dear I don't actually seem to have enough money.").open()
        } else {
            dialogue.then { cross(it, false) }.open()
        }
    }

    /**
     * Sends [plr] to the gate and through it. Nothing is taken from them until the gate has opened.
     *
     * @param plr The player.
     * @param free If the player is not charged the toll.
     */
    fun cross(plr: Player, free: Boolean) {
        // A player that is already on their way gets nothing more by asking again.
        if (plr.actions.first(Crossing::class.java) == null) {
            plr.submitAction(Crossing(plr, free))
        }
    }

    /**
     * Opens the gate and keeps the way through it shut to everyone else until it closes again.
     *
     * Everything is checked before anything is changed, so a gate that cannot be opened is left as it was.
     *
     * @param world The world.
     * @param plr The player that the gate is opening for.
     * @return `true` if the gate is now open, or `false` if it is busy or the closed leaves are not both standing.
     */
    private fun openGate(world: World, plr: Player): Boolean {
        if (busy) {
            return false
        }
        val closed = ArrayList<GameObject>(leaves.size)
        for (leaf in leaves) {
            closed += world.objects.findAll(leaf.position)
                .filter { it.id == leaf.type.closed }
                .findFirst().orElse(null) ?: return false
        }

        // Work out where the leaves swing to before anything is touched.
        val swings = closed.mapIndexed { index, leaf -> Doors.doubleSwap(leaf, leaves[index].type, true) }
        val removed = closed.filter { world.removeObject(it) }
        if (removed.size != closed.size) {
            removed.forEach { world.addObject(it.id, it.position, it.objectType, it.direction) }
            return false
        }

        val placed = ArrayList<GameObject>(leaves.size * 2)
        swings.forEach { placed += world.addObject(it.id, it.position, it.old.objectType, it.direction) }
        leaves.forEach { placed += world.addObject(BLOCKER, it.position, ObjectType.STRAIGHT_WALL, ObjectDirection.WEST) }
        busy = true
        plr.playSound(leaves.first().type.openSoundOrDefault)

        world.scheduleOnce(leaves.first().type.durationOrDefault) {
            try {
                placed.forEach { world.removeObject(it) }
                closed.forEach { world.addObject(it.id, it.position, it.objectType, it.direction) }
            } finally {
                busy = false
            }
        }
        return true
    }

    /**
     * Takes a player to the gate, waits for it to be free, and sends them through it.
     *
     * This is a weak action, so walking away, or doing anything else, cancels it. It cannot be a strong action because
     * those interrupt weak actions every tick, which is what the navigator walks the player to the gate with. The
     * crossing itself is a single step that is walked in the same tick as the one that opens the gate, so nothing is
     * left to lock the player for.
     *
     * @param plr The player.
     * @param free If the player is not charged the toll.
     */
    private class Crossing(plr: Player, private val free: Boolean) : Action<Player>(plr, ActionType.WEAK) {

        /**
         * If the player is going from the Lumbridge side to the Al Kharid side, as opposed to the other way around.
         */
        private val fromLumbridge = isLumbridgeSide(plr.position)

        /**
         * The tile that the player goes through the gate from.
         */
        private val start = startFor(fromLumbridge, rowFor(plr.position.y))

        /**
         * The pending walk to [start], or `null` if the player did not need one.
         */
        private var walking: CompletableFuture<NavigationResult>? = null

        /**
         * The ticks that have passed.
         */
        private var ticks = 0

        override fun run(): Boolean {
            if (++ticks > MAX_TICKS) {
                return stop("You can't get through the gate right now.")
            }

            // The navigator is left to finish before the player is sent through, so that it does not walk them back.
            val pending = walking
            if (pending != null && !pending.isDone) {
                return false
            }
            if (mob.position != start) {
                if (pending != null) {
                    return stop("You can't reach the gate from here.")
                }
                walking = mob.navigator.navigate(start, false)
                return false
            }
            if (busy) {
                return false
            }
            return cross()
        }

        /**
         * Takes the toll, opens the gate, and sends the player through. Always completes this action.
         */
        private fun cross(): Boolean {
            if (!free && mob.inventory.computeAmountForId(COINS) < TOLL) {
                return stop("You don't have enough coins to pay the toll.")
            }
            if (!openGate(world, mob)) {
                return stop("The gate seems to be stuck.")
            }
            if (!free) {
                mob.inventory.remove(Item(COINS, TOLL))
                mob.sendMessage("You pay the guard.")
            }
            mob.walking.addStep(if (fromLumbridge) Direction.EAST else Direction.WEST)
            return true
        }

        /**
         * Tells the player why they are not going through, and completes this action.
         */
        private fun stop(message: String): Boolean {
            mob.sendMessage(message)
            return true
        }
    }
}
