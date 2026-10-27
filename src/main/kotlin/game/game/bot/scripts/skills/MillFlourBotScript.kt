package game.bot.scripts.skills

import api.bot.Suspendable.naturalDelay
import api.bot.Suspendable.waitFor
import api.bot.script.BotScript
import api.bot.script.BotScriptData
import api.bot.webwalk.plan.BankAccess
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import api.predef.ext.get
import com.google.gson.JsonObject
import engine.bot.coordinator.skill.CookingScriptFactory
import engine.bot.coordinator.skill.SkillingScriptFactory
import game.bot.scripts.HarvestBotScript
import game.bot.scripts.HarvestBotScript.Companion.Harvestable
import game.content.flourMill.FlourMill
import game.content.flourMill.FlourMill.flourInBin
import game.content.flourMill.FlourMill.hopperFull
import io.luna.game.action.ActionType
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.Shop
import io.luna.game.model.item.shop.ShopInterface
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.`object`.GameObject
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Grinds owned grain into pots of flour at the Lumbridge, Ardougne or Zanaris flour mill.
 *
 * Each trip withdraws grain and empty pots, climbs to the hopper, grinds one grain at a time, then climbs down and
 * fills the pots from the flour bin before banking the flour. Flour already in the bot's bin is collected first.
 *
 * Missing grain queues one wheat-picking session in the mill's own field, with the mill session queued again once
 * behind it. Missing pots are bought at home from the general stores that stock them. Once a session has milled,
 * running out of grain ends it, and so does a shopping trip that finds no pots. Finished
 * flour stays banked for the market, or a dough session follows for bots that like to see a job through.
 *
 * @param bot The bot milling flour.
 * @property mill The mill this session uses.
 * @param duration Maximum time allowed for this session.
 * @property supplied If this session follows a wheat-picking session it queued, so it won't queue another.
 * @author TheLining
 * @author lare96
 */
class MillFlourBotScript(
    bot: Bot,
    val mill: BotMill,
    private val duration: Duration,
    private val supplied: Boolean = false
) : BotScript(bot) {

    companion object {

        /**
         * The flour mills bots can reach.
         *
         * TODO The Cooks' Guild mill needs the guild door (chef's hat and level 32 Cooking) before bots can walk in.
         *
         * @property top The tile beside the hopper and controls.
         * @property hopper The hopper's position.
         * @property controls The hopper controls' position.
         * @property ground The tile beside the flour bin.
         * @property bin The flour bin's position.
         * @property wheat The wheat field beside the mill.
         * @property bankStand The tile in front of the local booth or banker.
         * @property booth The bank booth's position, or the center of the configured banker search.
         * @property bankerId The native banker to use where the mill has no ground-floor bank booth.
         * @property arrival The travel destination to reenter after a supply trip leaves the mill's region.
         * @author lare96
         */
        enum class BotMill(val top: Position, val hopper: Position, val controls: Position,
                           val ground: Position, val bin: Position, val wheat: SubZone,
                           val bankStand: Position, val booth: Position,
                           val bankerId: Int? = null, val arrival: SubZone? = null) {
            LUMBRIDGE(top = Position(3165, 3306, 2),
                      hopper = Position(3166, 3307, 2),
                      controls = Position(3166, 3305, 2),
                      ground = Position(3165, 3305, 0),
                      bin = Position(3166, 3306, 0),
                      wheat = SubZone.LUMBRIDGE_MILL_WHEAT,
                      bankStand = Position(3092, 3245),
                      booth = Position(3091, 3245)),
            ARDOUGNE(top = Position(2630, 3385, 2),
                     hopper = Position(2632, 3386, 2),
                     controls = Position(2633, 3385, 2),
                     ground = Position(2631, 3384, 0),
                     bin = Position(2632, 3385, 0),
                     wheat = SubZone.ARDOUGNE_MILL_WHEAT,
                     bankStand = Position(2618, 3332),
                     booth = Position(2618, 3331)),
            ZANARIS(top = Position(2404, 4457, 1),
                    hopper = Position(2405, 4457, 1),
                    controls = Position(2404, 4456, 1),
                    ground = Position(2404, 4457),
                    bin = Position(2405, 4457),
                    wheat = SubZone.ZANARIS_MAIN,
                    bankStand = Position(2388, 4457),
                    booth = Position(2385, 4457),
                    bankerId = 498,
                    arrival = SubZone.ZANARIS_MAIN)
        }

        /**
         * The most grain one trip carries, leaving a slot for each pot.
         */
        private const val TRIP_SIZE = 14

        /**
         * Consecutive failed trips before stopping.
         */
        private const val MAX_FAILURES = 3

        /**
         * The most empty pots one shopping trip buys.
         */
        private const val POT_PURCHASE = 28

        /**
         * Coins taken shopping for pots.
         */
        private const val POT_COINS = 500

        private const val COINS = 995

        /**
         * How long a queued wheat-picking session lasts.
         */
        private val PICKING_TIME = 10.minutes

        /**
         * Saved mill, remaining session time and retry state.
         *
         * @author TheLining
         */
        class MillData : BotScriptData() {
            var mill = BotMill.LUMBRIDGE
            var duration = Duration.ZERO
            var supplied = false
            var failures = 0

            override fun load(data: JsonObject) {
                mill = BotMill.valueOf(data.get("mill").asString)
                duration = data.get("duration").asLong.milliseconds
                supplied = data.get("supplied")?.asBoolean ?: false
                failures = data.get("failures")?.asInt ?: 0
            }

            override fun save(data: JsonObject) {
                data.addProperty("mill", mill.name)
                data.addProperty("duration", duration.inWholeMilliseconds)
                data.addProperty("supplied", supplied)
                data.addProperty("failures", failures)
            }
        }

        /**
         * Picks a mill for [bot], preferring the one in its current area.
         */
        fun pickMill(bot: Bot): BotMill =
            BotMill.entries.minBy { if (it.wheat in bot.subZones) -1 else bot.position.computeLongestDistance(it.bin) }
    }

    /**
     * Monotonic elapsed time for this running instance.
     */
    private val started = TimeSource.Monotonic.markNow()

    /**
     * Consecutive failed trips, reset after a trip that banks flour.
     */
    private var failures = 0

    /**
     * If this session has finished a trip, so running out of grain ends it instead of picking more.
     */
    private var milled = false

    /**
     * Restores the mill, remaining session time and retry budget.
     */
    constructor(bot: Bot, data: MillData) : this(bot, data.mill, data.duration, data.supplied) {
        failures = data.failures
    }

    /**
     * Total of [id] across the inventory and bank.
     */
    private fun owned(id: Int) = bot.inventory.computeAmountForId(id).toLong() + bot.bank.computeAmountForId(id)

    /**
     * How much grain the next trip grinds: limited by grain, by pots left after the bin's flour, and by the bin.
     */
    private fun tripGrain(): Int {
        val pots = owned(FlourMill.POT) - bot.flourInBin
        val room = FlourMill.BIN_CAPACITY - bot.flourInBin
        return minOf(TRIP_SIZE.toLong(), owned(FlourMill.GRAIN), pots, room.toLong()).coerceAtLeast(0).toInt()
    }

    override suspend fun run(): Boolean {
        if (failures >= MAX_FAILURES || started.elapsedNow() >= duration) return true
        if (!isActionSafe() || bot.actions.size(ActionType.WEAK) > 0) return false
        if (owned(FlourMill.GRAIN) == 0L && bot.flourInBin == 0) return milled || queueWheat()
        if (owned(FlourMill.POT) == 0L) {
            if (owned(COINS) == 0L) return true
            val bought = withTimeoutOrNull(300_000) { buyPots() } == true
            if (!bought) return true
        }
        val grain = tripGrain()
        val success = withTimeoutOrNull(600_000) { trip(grain) } == true
        if (success) {
            failures = 0
            milled = true
        } else if (isActionSafe()) {
            failures++
        }
        return failures >= MAX_FAILURES
    }

    /**
     * Queues one wheat-picking session in the mill's field, then this mill again behind it. Returns `true` to end
     * this session.
     */
    private fun queueWheat(): Boolean {
        if (supplied) return true
        bot.scriptStack.push(HarvestBotScript(bot, Harvestable.WHEAT, PICKING_TIME, mutableListOf(mill.wheat)))
        val remaining = (duration - started.elapsedNow()).coerceAtLeast(Duration.ZERO)
        bot.scriptStack.push(MillFlourBotScript(bot, mill, remaining, supplied = true))
        return true
    }

    /**
     * Buys empty pots at home from the general stores that stock them, fullest first, as bots shop elsewhere, and
     * banks them.
     */
    private suspend fun buyPots(): Boolean {
        val wanted = minOf(owned(FlourMill.GRAIN) + bot.flourInBin, POT_PURCHASE.toLong()).toInt()
        if (SubZone.HOME !in bot.subZones && !handler.webWalkTo(SubZone.HOME)) return false
        val banking = bot.actionHandler.banking
        if (!banking.travelToBankOpen() || !banking.depositInventory() || !isActionSafe()) return false
        banking.clickBankingMode(false)
        if (!banking.withdraw(Item(COINS, POT_COINS)) || COINS !in bot.inventory) return false
        if (!bot.actionHandler.widgets.clickCloseInterface()) return false
        val shops = world.shops.values().filter { it.currency == Currency.COINS && it.botAccess.apply(bot) && potStock(it) > 0 }
            .sortedByDescending { potStock(it) }
        for (shop in shops) {
            if (bot.inventory.computeAmountForId(FlourMill.POT) >= wanted) break
            bot.overlays.open(ShopInterface(shop))
            if (bot.overlays[ShopInterface::class]?.shop !== shop) continue
            while (bot.inventory.computeAmountForId(FlourMill.POT) < wanted) {
                val before = bot.inventory.computeAmountForId(FlourMill.POT)
                val left = wanted - before
                val bought = when {
                    left >= 10 -> handler.shop.buy10(FlourMill.POT).await()
                    left >= 5 -> handler.shop.buy5(FlourMill.POT).await()
                    else -> handler.shop.buy1(FlourMill.POT).await()
                }
                if (!bought || bot.inventory.computeAmountForId(FlourMill.POT) <= before) break
            }
        }
        bot.actionHandler.widgets.clickCloseInterface()
        return FlourMill.POT in bot.inventory && banking.travelToBankDepositAll()
    }

    /**
     * Withdraws supplies for [grain] grain, grinds it, collects every pot of flour in the bin, and banks the flour.
     */
    private suspend fun trip(grain: Int): Boolean {
        val banking = bot.actionHandler.banking
        if (!openMillBank() || !banking.depositInventory() || !isActionSafe()) return false
        banking.clickBankingMode(false)
        val pots = minOf(owned(FlourMill.POT), (grain + bot.flourInBin).toLong()).toInt()
        val wanted = listOfNotNull(if (grain > 0) Item(FlourMill.GRAIN, grain) else null, Item(FlourMill.POT, pots))
        if (!banking.withdrawAll(wanted) || !isActionSafe()) return false
        if (!bot.actionHandler.widgets.clickCloseInterface() || !isActionSafe()) return false

        if (grain > 0) {
            if (!handler.webWalkTo(mill.top, bank = BankAccess.NONE) || !isActionSafe()) return false
            while (FlourMill.GRAIN in bot.inventory && bot.flourInBin < FlourMill.BIN_CAPACITY) {
                if (!grind()) return false
            }
        }

        if (!handler.webWalkTo(mill.ground, bank = BankAccess.NONE) || !isActionSafe()) return false
        while (bot.flourInBin > 0 && FlourMill.POT in bot.inventory) {
            if (!collect()) return false
        }
        return isActionSafe() && openMillBank() && banking.depositInventory()
    }

    /**
     * Returns through the existing travel system when necessary, then opens the mill's local booth or banker.
     * Zanaris bankers are stationary NPCs; its decorative bank object is on another plane and cannot open a bank.
     */
    private suspend fun openMillBank(): Boolean {
        val arrival = mill.arrival
        if (arrival != null && arrival !in bot.subZones && !handler.webWalkTo(arrival)) return false
        if (!handler.webWalkTo(mill.bankStand, bank = BankAccess.NONE) || !isActionSafe()) return false
        val bank = if (mill.bankerId != null) {
            world.locator.findNpcs(mill.booth, 8) {
                it.id == mill.bankerId && it.state == EntityState.ACTIVE && it.position.z == mill.bankStand.z
            }.minByOrNull { it.position.computeLongestDistance(bot.position) }
        } else {
            world.locator.findObjectsOnTile(mill.booth) { it.def().actions.contains("Use-quickly") }.firstOrNull()
        } ?: return false
        return bot.actionHandler.banking.open(bank)
    }

    /**
     * How many empty pots [shop] has in stock.
     */
    private fun potStock(shop: Shop): Int {
        val index = shop.items.computeIndexForId(FlourMill.POT)
        return if (index == -1) 0 else shop.items[index]?.amount ?: 0
    }

    /**
     * Puts one grain in the hopper, if it's empty, and pulls the controls.
     */
    private suspend fun grind(): Boolean {
        if (!bot.hopperFull) {
            val hopper = millObject(mill.hopper, FlourMill.HOPPERS) ?: return false
            if (!handler.inventory.useItem(FlourMill.GRAIN).onObject(hopper)) return false
            if (!waitFor(5.seconds) { bot.hopperFull }) return false
            bot.naturalDelay()
        }
        val before = bot.flourInBin
        val controls = millObject(mill.controls, FlourMill.CONTROLS) ?: return false
        if (!handler.interactions.interact(1, controls)) return false
        if (!waitFor(5.seconds) { bot.flourInBin > before }) return false
        bot.naturalDelay()
        return true
    }

    /**
     * Fills one empty pot from the flour bin.
     */
    private suspend fun collect(): Boolean {
        val before = bot.inventory.computeAmountForId(FlourMill.POT_OF_FLOUR)
        val bin = millObject(mill.bin, setOf(FlourMill.FLOUR_BIN)) ?: return false
        if (!handler.interactions.interact(1, bin)) return false
        if (!waitFor(5.seconds) { bot.inventory.computeAmountForId(FlourMill.POT_OF_FLOUR) > before }) return false
        bot.naturalDelay()
        return true
    }

    /**
     * The loaded mill object at [position] with one of [ids].
     */
    private fun millObject(position: Position, ids: Set<Int>): GameObject? =
        world.locator.findObjectsOnTile(position) { it.id in ids }.firstOrNull()

    override suspend fun completed() {
        // Bots that see a job through turn their flour into dough; the rest leave it banked for the market.
        if (owned(FlourMill.POT_OF_FLOUR) > 0 && (rand(bot.personality.intelligence) || bot.personality.isDextrous)) {
            CookingScriptFactory.getPreparationScript(bot, bot.cooking.staticLevel)?.let { bot.scriptStack.pushTail(it, 2) }
        }
    }

    override fun snapshot(): MillData = MillData().also {
        it.mill = mill
        it.duration = (duration - started.elapsedNow()).coerceAtLeast(Duration.ZERO)
        it.supplied = supplied
        it.failures = failures
    }
}
