package world.minigame.party_room.drop_party

import api.attr.Attr
import api.predef.*
import com.google.common.base.Stopwatch
import com.google.common.collect.ImmutableList
import com.google.common.math.IntMath
import com.google.common.primitives.Ints
import io.luna.Luna
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.item.Item
import io.luna.game.model.item.ItemContainer
import io.luna.game.model.mob.Animation
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection
import io.luna.game.model.`object`.ObjectType
import io.luna.game.task.Task
import world.minigame.party_room.PartyRoom
import world.minigame.party_room.PartyRoomOption
import java.time.Duration
import java.util.*
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Handles the lever option for the "Drop Party" event.
 */
object DropPartyOption : PartyRoomOption(200_000, "Drop Party") {

    /**
     * Handles the countdown.
     */
    class DropPartyCountdown : Task(true, 2) {
        private val secondsNeeded = Ints.saturatedCast(computeCountdownTime().toSeconds())

        override fun execute() {
            secondsLeft = secondsNeeded - executionCounter;
            if (executionCounter >= secondsNeeded) {
                world.schedule(DropPartyTask())
                cancel()
            }
        }
    }

    /**
     * Handles the dropping of balloons.
     */
    class DropPartyTask : Task(true, BALLOON_DROP_FREQUENCY) {

        /**
         * Computes all the items that need to be dropped before the party can end, unless we're in impatient mode.
         */
        private val balloonItems = mutableListOf<Item>().apply {
            var total = floor(chest.items.size() * BALLOON_MULTIPLIER).roundToInt()
            total -= chest.items.size()
            addAll(chest.items.filterNotNull())
            repeat(total) {
                if (rand().nextBoolean()) {
                    val junkItem = Item(JUNK_ITEMS.random())
                    add(junkItem)
                } else {
                    add(Item(592))
                }
            }
        }

        /**
         * Computes all the items that need to be dropped before the party can end. Unless we're in impatient mode.
         */
        private val spawnPositions = LinkedList<Position>().apply {
            buildSpawnPositions(this)
        }

        /**
         * The timer for event patience.
         */
        private val patienceTimer = Stopwatch.createUnstarted()

        override fun onSchedule(): Boolean {
            patienceTimer.start()
            return true
        }

        override fun execute() {
            balloonItems.shuffle()
            spawnPositions.shuffle()
            val impatient = patienceTimer.elapsed() >= PATIENCE_THRESHOLD
            val items = balloonItems.iterator()
            var spawned = 0
            while (items.hasNext()) {
                val nextItem = items.next()
                if (spawned >= MAX_BALLOON_SPAWNS) { // Don't spawn too many balloons at once.
                    break
                }
                val randomPos = spawnPositions.peekFirst() // Get a random position and verify nothing is already on it.
                val nextPos = if (randomPos == null || world.objects.isOccupied(randomPos)) null else randomPos
                if (nextPos == null) {
                    if (impatient) {
                        // We've been waiting too long to drop more balloons, start discarding balloons.
                        items.remove()
                    }
                    continue
                }
                world.addObject(BalloonObject(nextPos, nextItem))
                spawnPositions.removeFirst()
                items.remove()
                spawned++
            }
            if (spawnPositions.isEmpty()) {
                buildSpawnPositions(spawnPositions)
            }
            if (balloonItems.isEmpty()) {
                chest.items.clear()
                cancel()
                PartyRoom.resetLeverOption()
            }
        }

        /**
         * Builds a list of all the possible positions to spawn balloons on.
         */
        private fun buildSpawnPositions(list: LinkedList<Position>) {
            BALLOON_LOCS.forEach { list.addAll(it.computeAllPositions()) }
            list.shuffle()
        }
    }

    /**
     * Represents the chest in the party room.
     */
    class DropPartyChest {

        /**
         * The items currently in the party room chest. In the event of graceful shutdowns, this data will be saved to
         * prevent the loss of items.
         */
        val items = ItemContainer(216, ItemContainer.StackPolicy.STANDARD, 2273)

        /**
         * If the party room chest is locked from regular players being able to deposit items.
         */
        var locked = false
    }

    /**
     * A data class representing a balloon object.
     */
    data class BalloonObject(private val pos: Position, val item: Item) :
            GameObject(ctx, BALLOON_IDS.random(), pos, ObjectType.DEFAULT, ObjectDirection.SOUTH, Optional.empty()) {
        var stompedBy: Player? = null

        override fun onActive() {
            startDespawnTask()
        }

        fun stomp(plr: Player) {
            if (stompedBy == null) {
                stompedBy = plr
                plr.sendMessage("You stomp on the balloon.")
                plr.animation(Animation(794))
                animate(499)
                world.scheduleOnce(1) {
                    if (world.removeObject(this)) {
                        animate(-1)
                        world.addItem(item.id, item.amount, position, plr)
                    }
                }
            } else {
                plr.sendMessage("${stompedBy!!.username} has already claimed this balloon!")
            }
        }

        /**
         * Despawns this balloon after 10 minutes.
         */
        private fun startDespawnTask() {
            world.schedule(Duration.ofMinutes(10)) {
                if(state == EntityState.ACTIVE) {
                    world.removeObject(this)
                }
            }
        }
    }

    /**
     * Locations where balloons can potentially drop.
     */
    val BALLOON_LOCS = ImmutableList.of(
            area { swX = 2730; swY = 3463; neX = 2744; neY = 3467; },
            area { swX = 2731; swY = 3469; neX = 2744; neY = 3476; }
    )

    /**
     * Balloon object IDs mapped to their animation IDs.
     */
    val BALLOON_IDS = arrayListOf(115, 116, 117, 118, 119, 120)

    /**
     * How many balloons will drop relative to items in the chest.
     */
    val BALLOON_MULTIPLIER = 1.5


    /**
     * All possible junk items.
     */
    val JUNK_ITEMS = listOf(592, 526)

    /**
     * The max balloon spawns per cycle.
     */
    val MAX_BALLOON_SPAWNS = 15

    /**
     * How often balloons are dropped.
     */
    val BALLOON_DROP_FREQUENCY = Duration.ofSeconds(5).toTicks()

    /**
     * If the drop party hasn't ended after this time, balloons are skipped.
     */
    val PATIENCE_THRESHOLD = Duration.ofMinutes(30)


    /**
     * Items that a player has selected, but not yet confirmed to be deposited.
     */
    val Player.depositItems by Attr.itemContainer(8, ItemContainer.StackPolicy.STANDARD, 2274)
        .persist("drop_party_items")

    /**
     * The party room chest.
     */
    val chest = DropPartyChest()

    /**
     * How many seconds are left in the countdown.
     */
    var secondsLeft: Int? = null

    override fun canExecute(plr: Player): Boolean {
        if (chest.items.size() < 8) {
            if (Luna.settings().betaMode()) {
                ItemDefinition.ALL.filterNotNull().filter { it.value > 25_000 && !it.isStackable && it.isTradeable }
                    .map { Item(it.id) }
                    .shuffled().forEach { chest.items.add(it) }
                plr.sendMessage("[BETA] Filling chest with random items above 25,000 gold value.")
                return true
            }
            plr.sendMessage("There must be at least 8 items in the chest to start a drop party.")
            return false
        }
        if (Luna.settings().betaMode()) {
            plr.sendMessage("[BETA] Drop party will fast-forward to start in 10 seconds.")
        }
        return true
    }

    override fun execute(plr: Player) {
        world.schedule(DropPartyCountdown())
    }

    /**
     * Compute the total cost of all items in the chest.
     */
    private fun computeTotalCost(): Int {
        var totalCost = 0
        for (item in chest.items) {
            if (item == null) {
                continue
            }
            totalCost = IntMath.saturatedAdd(totalCost, item.itemDef.value * item.amount)
        }
        return totalCost
    }

    /**
     * Computes the total countdown time based on the cost.
     */
    private fun computeCountdownTime(): Duration {
        if (Luna.settings().betaMode()) {
            return Duration.ofSeconds(10)
        }
        var totalCost = computeTotalCost()
        return when {
            totalCost > 1_000_000_000 -> Duration.ofMinutes(60)
            totalCost > 500_000_000 -> Duration.ofMinutes(30)
            totalCost > 100_000_000 -> Duration.ofMinutes(15)
            totalCost > 10_000_000 -> Duration.ofMinutes(5)
            else -> Duration.ofMinutes(1)
        }
    }
}