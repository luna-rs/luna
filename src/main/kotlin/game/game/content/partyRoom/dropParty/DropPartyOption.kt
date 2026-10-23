package game.content.partyRoom.dropParty

import api.attr.Attr
import api.attr.getValue
import api.predef.*
import api.predef.ext.*
import com.google.common.base.Stopwatch
import com.google.common.collect.ImmutableList
import com.google.common.math.LongMath
import com.google.common.primitives.Ints
import game.content.partyRoom.PartyRoom
import game.content.partyRoom.PartyRoomOption
import engine.bot.coordinator.MinigameCoordinator
import game.skill.cooking.cookFood.Food
import io.luna.Luna
import io.luna.game.model.EntityState
import io.luna.game.model.LocalGraphic
import io.luna.game.model.Position
import io.luna.game.model.area.Area
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.item.Item
import io.luna.game.model.item.GroundItem
import io.luna.game.model.item.ItemContainer
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection
import io.luna.game.model.`object`.ObjectType
import io.luna.game.task.Task
import java.time.Duration
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Handles the lever option for the "Drop Party" event.
 *
 * @author lare96
 */
object DropPartyOption : PartyRoomOption(200_000, "Drop Party") {

    /**
     * Handles the countdown.
     *
     * @property balloonItems The funded items and generated junk to drop after the countdown.
     * @property countdown The duration calculated from the funded value before the chest is cleared.
     *
     * @author lare96
     */
    class DropPartyCountdown(val balloonItems: MutableList<Item>, val countdown: Duration) : Task(false, 1) {
        /**
         * The countdown rounded up to whole 600-millisecond game ticks.
         */
        private val ticksNeeded = Ints.saturatedCast((countdown.toMillis() + 599) / 600)

        override fun execute() {
            val ticksLeft = (ticksNeeded - executionCounter - 1).coerceAtLeast(0)
            secondsLeft = (ticksLeft * 600 + 999) / 1000
            if (ticksLeft == 0) {
                secondsLeft = 0
                world.schedule(DropPartyTask(balloonItems))
                cancel()
            }
        }
    }

    /**
     * Handles the dropping of balloons.
     *
     * @property balloonItems The remaining rewards consumed as balloons are spawned.
     *
     * @author lare96
     */
    class DropPartyTask(val balloonItems: MutableList<Item>) : Task(true, BALLOON_DROP_FREQUENCY.toTicks()) {

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
                cancel()
                PartyRoom.resetLeverOption()
            }
        }

        /**
         * Builds a list of all the possible positions to spawn balloons on.
         */
        private fun buildSpawnPositions(list: ArrayList<Position>) {
            BALLOON_LOCS.forEach { list.addAll(it.positions) }
            list.shuffle()
        }
    }

    /**
     * Represents the chest in the party room.
     *
     * @author lare96
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
     *
     * @property pos The native spawn position of the balloon.
     * @property item The concealed reward released when the balloon is stomped.
     * @property partyId The runtime party that owns this balloon.
     *
     * @author lare96
     */
    data class BalloonObject(private val pos: Position, val item: Item, val partyId: Long = currentPartyId) :
        GameObject(ctx,
                   BALLOON_IDS.random(),
                   pos,
                   ObjectType.DEFAULT,
                   ObjectDirection.SOUTH,
                   ChunkUpdatableView.globalView(),
                   true) {
        /**
         * The exact native reward created for the claimant, including its ownership view.
         */
        var reward: GroundItem? = null
            private set

        /**
         * The first player to claim this balloon, or null while it remains available.
         */
        var stompedBy: Player? = null

        override fun onActive() {
            activeBalloons.add(this)
            startDespawnTask()
        }

        override fun onInactive() {
            activeBalloons.remove(this)
        }

        /**
         * Claims the balloon for [plr] and schedules its native, owner-visible reward drop.
         */
        fun stomp(plr: Player) {
            if (stompedBy == null) {
                stompedBy = plr
                plr.sendMessage("You stomp on the balloon.")
                plr.animation(Animation(794))
                world.scheduleOnce(1) {
                    LocalGraphic(ctx, 92, 0, 0, position, ChunkUpdatableView.globalView()).display()
                    if (world.removeObject(this)) {
                        reward = spawnReward(item, position, plr)
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
     * Resolves the exact ground entity produced by the native drop, including a replacement stack created by merging.
     * Existing same-tile drops and other players' ownership views cannot be mistaken for a new reward.
     */
    internal fun spawnReward(item: Item, position: Position, plr: Player): GroundItem? {
        val previous = world.locator.findItems(position, 0) { true }
        val spawned = world.addItem(item, position, plr)
        if (spawned.state == EntityState.ACTIVE) return spawned
        return world.locator.findItems(position, 0) {
            it.id == item.id && it.view == spawned.view && it !in previous
        }.singleOrNull()
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
     * The percentage of items that will be 'junk' relative to user-placed items. `1.0` = equal amount of junk items
     * as there are user-placed items, `0.0` = no junk items at all.
     */
    val JUNK_MULTIPLIER = 0.5

    /**
     * All possible junk items.
     */
    val JUNK_ITEMS = mutableListOf<Int>().apply {
        add(526)
        add(592)
        Food.RAW_TO_FOOD.values.forEach { add(it.burnt) }
    }

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
    val Player.depositItems by Attr.obj { ItemContainer(8, ItemContainer.StackPolicy.STANDARD, 2274) }
        .persist("drop_party_items")

    /**
     * The party room chest.
     */
    val chest = DropPartyChest()

    /**
     * Active native balloons retained until removal, including those left after the lever resets.
     */
    private val activeBalloons = mutableSetOf<BalloonObject>()

    /**
     * Identifies the current runtime party; restored attendance cannot accidentally join a different party.
     */
    var currentPartyId = 0L
        private set
    /**
     * Funded value captured before the chest is cleared, used to determine the native countdown.
     */
    var currentPartyValue = 0L
        private set
    /**
     * Maximum runtime lifetime including countdown, balloon spawning patience and final balloon expiry.
     */
    var currentPartyDeadline = 0L
        private set

    /**
     * Includes balloons remaining on the floor after the lever becomes available again.
     */
    fun isCurrentParty(id: Long): Boolean = id != 0L && id == currentPartyId &&
        System.currentTimeMillis() < currentPartyDeadline &&
        (PartyRoom.option === this || activeBalloons.any { it.partyId == id })

    /**
     * Returns only visible, unclaimed world objects belonging to this runtime party.
     */
    fun availableBalloons(id: Long): List<BalloonObject> = activeBalloons.filter {
        it.partyId == id && it.state == EntityState.ACTIVE && it.stompedBy == null
    }

    /**
     * Stages a donation through the same checked inventory transfer used by the chest interface.
     */
    fun stageDeposit(plr: Player, id: Int, index: Int, amount: Int): Boolean {
        if (!plr.overlays.has(DropPartyInterface::class.java) || amount <= 0 ||
            chest.locked && plr.rights < RIGHTS_ADMIN) return false
        if (index !in 0 until plr.inventory.capacity()) return false
        val held = plr.inventory[index] ?: return false
        if (held.id != id || !held.itemDef.isTradeable) return false
        val count = minOf(amount, plr.inventory.computeAmountForId(id),
            if (held.itemDef.isStackable) Int.MAX_VALUE else 8)
        val transfer = held.withAmount(count)
        if (!plr.depositItems.hasSpaceFor(transfer) || !plr.inventory.remove(transfer)) return false
        if (!plr.depositItems.add(transfer)) {
            check(plr.inventory.add(transfer)) { "Could not restore rejected party deposit." }
            return false
        }
        return true
    }

    /**
     * Transfers only accepted staged items; a full or locked chest leaves the remaining items with the player.
     */
    fun confirmDeposit(plr: Player): Boolean {
        if (!plr.overlays.has(DropPartyInterface::class.java) ||
            chest.locked && plr.rights < RIGHTS_ADMIN) return false
        var moved = false
        for (item in plr.depositItems.filterNotNull().toList()) {
            if (!item.itemDef.isTradeable || !chest.items.hasSpaceFor(item)) continue
            if (chest.items.add(item)) {
                check(plr.depositItems.remove(item)) { "Staged party deposit changed during confirmation." }
                moved = true
            }
        }
        return moved
    }

    /**
     * How many seconds are left in the countdown.
     */
    var secondsLeft: Int? = null

    override fun canExecute(plr: Player): Boolean {
        val isBetaMode = Luna.settings().game().betaMode()
        if (chest.items.size() < 8) {
            if (isBetaMode) {
                ItemDefinition.ALL.filterNotNull().filter { (it.value > 20_000 || it.isStackable) && it.isTradeable }
                    .map { if (it.isStackable) Item(it.id(), rand(1..Int.MAX_VALUE)) else Item(it.id()) }
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
        startParty()
    }

    /**
     * Starts the same party lifecycle for a player lever pull or an automatic server start.
     */
    fun startParty() {
        currentPartyValue = computeTotalCost()
        val countdown = computeCountdownTime(currentPartyValue)
        currentPartyId = maxOf(System.currentTimeMillis(), currentPartyId + 1)
        currentPartyDeadline = System.currentTimeMillis() + countdown.toMillis() +
            PATIENCE_THRESHOLD.plusMinutes(10).toMillis()
        // Add all user-placed items.
        val balloonItems = mutableListOf<Item>()
        balloonItems.addAll(chest.items.filterNotNull())

        // Add junk items.
        val junkCount = floor(chest.items.size() * JUNK_MULTIPLIER).roundToInt()
        repeat(junkCount) { balloonItems += Item(JUNK_ITEMS.random()) }

        // Start countdown, clear chest.
        chest.items.clear()
        secondsLeft = Ints.saturatedCast(countdown.seconds)
        world.schedule(DropPartyCountdown(balloonItems, countdown))
        val attendingParty = currentPartyId
        world.schedule(object : Task(true, 5) {
            override fun execute() {
                if (!isCurrentParty(attendingParty)) {
                    cancel()
                    return
                }
                MinigameCoordinator.onPartyStarted()
            }
        })
    }

    /**
     * Compute the total cost of all items in the chest.
     */
    internal fun computeTotalCost(): Long {
        var totalCost = 0L
        for (item in chest.items) {
            if (item == null) {
                continue
            }
            totalCost = LongMath.saturatedAdd(totalCost, item.itemDef.value.toLong().coerceAtLeast(0) * item.amount)
        }
        return totalCost
    }

    /**
     * Computes the total countdown time based on the cost.
     */
    internal fun computeCountdownTime(totalCost: Long): Duration {
        val isBetaMode = Luna.settings().game().betaMode()
        if (isBetaMode) {
            return Duration.ofSeconds(10)
        }
        return when {
            totalCost > 1_000_000_000 -> Duration.ofMinutes(60)
            totalCost > 500_000_000 -> Duration.ofMinutes(30)
            totalCost > 100_000_000 -> Duration.ofMinutes(15)
            totalCost > 10_000_000 -> Duration.ofMinutes(5)
            else -> Duration.ofMinutes(1)
        }
    }
}
