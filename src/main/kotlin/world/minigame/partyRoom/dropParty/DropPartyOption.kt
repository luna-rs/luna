package world.minigame.partyRoom.dropParty

import api.attr.Attr
import api.attr.getValue
import api.predef.*
import api.predef.ext.*
import com.google.common.base.Stopwatch
import com.google.common.collect.ImmutableList
import com.google.common.math.IntMath
import com.google.common.primitives.Ints
import io.luna.Luna
import io.luna.game.model.Area
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.item.Item
import io.luna.game.model.item.ItemContainer
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection
import io.luna.game.model.`object`.ObjectType
import io.luna.game.task.Task
import world.minigame.partyRoom.PartyRoom
import world.minigame.partyRoom.PartyRoomOption
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
                secondsLeft = 0
                world.schedule(DropPartyTask())
                cancel()
            }
        }
    }

    /**
     * Handles the dropping of balloons.
     */
    class DropPartyTask : Task(true, BALLOON_DROP_FREQUENCY.toTicks()) {

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
        private val spawnPositions = arrayListOf<Position>().apply {
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
            val impatient = patienceTimer.elapsed() >= PATIENCE_THRESHOLD
            val items = balloonItems.iterator()
            var spawned = 0
            while (items.hasNext()) {
                val nextItem = items.next()
                if (spawned >= MAX_BALLOON_SPAWNS) { // Don't spawn too many balloons at once.
                    break
                }
                val totalSpawns = spawnPositions.size
                if (totalSpawns == 0) {
                    break
                }
                val randomIndex = if (totalSpawns == 1) 0 else rand().nextInt(0, totalSpawns)
                val randomPos: Position =
                    spawnPositions[randomIndex] // Get a random position and verify nothing is already on it.
                val nextPos = if (world.objects.isOccupied(randomPos)) null else randomPos
                if (nextPos == null) {
                    if (impatient) {
                        // We've been waiting too long to drop more balloons, start discarding items.
                        items.remove()
                    }
                    continue
                }
                world.addObject(BalloonObject(nextPos, nextItem))
                spawnPositions.removeAt(randomIndex)
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
        private fun buildSpawnPositions(list: ArrayList<Position>) {
            BALLOON_LOCS.forEach { list.addAll(it.positionSet) }
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
        GameObject(ctx, BALLOON_IDS.random(), pos, ObjectType.DEFAULT, ObjectDirection.SOUTH, ChunkUpdatableView.globalView(), true) {
        var stompedBy: Player? = null

        override fun onActive() {
            startDespawnTask()
        }

        fun stomp(plr: Player) {
            if (stompedBy == null) {
                stompedBy = plr
                plr.sendMessage("You stomp on the balloon.")
                plr.animation(Animation(794))
                animate()
                world.scheduleOnce(1) {
                    if (world.removeObject(this)) {
                        // animate(-1)
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
            world.scheduleOnce(Duration.ofMinutes(10)) {
                if (state == EntityState.ACTIVE) {
                    world.removeObject(this)
                }
            }
        }
    }

    /**
     * Locations where balloons can potentially drop.
     */
    val BALLOON_LOCS = ImmutableList.of(
        Area.of(2730, 3463, 2744, 3467),
        Area.of(2731, 3469, 2744, 3476)
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
    val BALLOON_DROP_FREQUENCY = Duration.ofSeconds(5)

    /**
     * If the drop party hasn't ended after this time, balloons are skipped.
     */
    val PATIENCE_THRESHOLD = Duration.ofMinutes(30)


    /**
     * Items that a player has selected, but not yet confirmed to be deposited.
     */
    val Player.depositItems by Attr.obj(ItemContainer(8, ItemContainer.StackPolicy.STANDARD, 2274))
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
        val isBetaMode = Luna.settings().game().betaMode()
        if (chest.items.size() < 8) {
            if (isBetaMode) {
                ItemDefinition.ALL.filterNotNull().filter { it.value > 20_000 && !it.isStackable && it.isTradeable }
                    .map { Item(it.id) }
                    .shuffled().forEach { chest.items.add(it) }
                plr.sendMessage("[BETA] Filling chest with random items above 20,000 gold value.")
                return true
            }
            plr.sendMessage("There must be at least 8 items in the chest to start a drop party.")
            return false
        }
        if (isBetaMode) {
            plr.sendMessage("[BETA] Drop party will fast-forward to start in 10 seconds.")
        }
        return true
    }

    override fun execute(plr: Player) {
        val itemCount = chest.items.size()
        val botCount = when {
            itemCount < 15 -> rand(2, 4)
            itemCount < 25 -> rand(4, 8)
            itemCount < 50 -> rand(6, 12)
            itemCount < 100 -> rand(8, 16)
            itemCount < 150 -> rand(10, 20)
            itemCount < 175 -> rand(12, 24)
            else -> rand(14, 28)
        }
        if (botCount > 0) {
            // TODO log in random bots with drop party script?
            //val bot = Bot.Builder(ctx).setScript { DropPartyBotScript(it) }.build()
            //bot.login()
        }
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
        val isBetaMode = Luna.settings().game().betaMode()
        if (isBetaMode) {
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