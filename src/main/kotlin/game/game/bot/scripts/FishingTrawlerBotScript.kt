package game.bot.scripts

import api.bot.Suspendable.delay
import api.bot.Suspendable.waitFor
import api.bot.script.BotScript
import api.bot.script.BotScriptData
import api.predef.*
import api.predef.ext.*
import com.google.gson.JsonObject
import engine.controllers.Controllers.inWilderness
import game.content.fishingTrawler.FishingTrawler
import game.content.fishingTrawler.FishingTrawler.catchRolls
import game.content.fishingTrawler.FishingTrawler.unclaimedCatch
import game.content.fishingTrawler.TrawlerCatchInterface
import io.luna.game.model.item.Item
import io.luna.game.model.item.GroundItem
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Uses existing owned supplies for one trawler trip, repairing visible damage and bailing the hold.
 * Interrupted trips are abandoned without a reward; persisted catches remain available at the net.
 *
 * @author lare96
 */
class FishingTrawlerBotScript(bot: Bot, private var boarded: Boolean = false,
                              private val deadline: Long = System.currentTimeMillis() + 1_200_000) : BotScript(bot) {
    companion object {
        fun eligible(bot: Bot): Boolean {
            if (!ActionSafety.NON_COMBAT.allows(bot) || bot.inWilderness() || bot.fishing.level < 15) return false
            if (bot.catchRolls > 0 || bot.unclaimedCatch.isNotEmpty()) return true
            return supplies(bot) != null
        }

        /**
         * Reserves owned supplies across both containers, leaving room for the bucket and catch.
         */
        internal fun supplies(bot: Bot): List<Item>? {
            fun owned(id: Int): Long = bot.inventory.computeAmountForId(id).toLong() +
                bot.bank.computeAmountForId(id)
            val paste = owned(1941).coerceAtMost(200).toInt()
            val ropes = owned(954).coerceAtMost(20).toInt()
            val bucket = when {
                owned(583) > 0 -> 583
                owned(585) > 0 -> 585
                else -> return null
            }
            if (paste <= 0 || ropes <= 0) return null
            return listOf(Item(1941, paste), Item(954, ropes), Item(bucket))
        }
    }
    /**
     * Records whether the one-shot voyage was already attempted.
     *
     * @author lare96
     */
    class VoyageData : BotScriptData() {
        var boarded = false
        var deadline = 0L
        override fun load(data: JsonObject) {
            boarded = data.get("boarded").asBoolean
            deadline = data.get("deadline").asLong
        }
        override fun save(data: JsonObject) {
            data.addProperty("boarded", boarded)
            data.addProperty("deadline", deadline)
        }
    }

    constructor(bot: Bot, data: VoyageData) : this(bot, data.boarded, data.deadline)

    override suspend fun init(resumed: Boolean): Boolean {
        if (!isActionSafe()) return true
        if (System.currentTimeMillis() >= deadline) return true
        if (boarded && bot.catchRolls <= 0 && bot.unclaimedCatch.isEmpty()) return true
        if (bot.fishing.level < 15) return true
        if (bot.inWilderness()) return true
        return withTimeoutOrNull(180_000) {
            if (bot.catchRolls > 0 || bot.unclaimedCatch.isNotEmpty()) {
                return@withTimeoutOrNull !handler.webWalkTo(FishingTrawler.DOCK, radius = 3)
            }
            val supplies = supplies(bot) ?: return@withTimeoutOrNull true
            // retrieveAll requires each requested quantity in one container. Consolidate split stacks first.
            if (!handler.banking.travelToBankDepositAll() || !handler.retrieveAll(supplies) ||
                !handler.widgets.clickCloseInterface() || !handler.webWalkTo(FishingTrawler.DOCK, radius = 3)) return@withTimeoutOrNull true
            if (bot.inventory.contains(585)) {
                if (!handler.inventory.clickItem(1, 585) ||
                    !waitFor(5.seconds) { bot.inventory.contains(583) }) return@withTimeoutOrNull true
            }
            if (bot.catchRolls > 0 || bot.unclaimedCatch.isNotEmpty()) return@withTimeoutOrNull false
            val plank = world.locator.findObjects(bot.position, 20) { it.id == 2178 }.firstOrNull()
                ?: return@withTimeoutOrNull true
            boarded = true
            if (!handler.interactions.interact(1, plank)) return@withTimeoutOrNull true
            !waitFor(90.seconds) { FishingTrawler.atSea(bot.position) && !bot.isLocked }
        } ?: true
    }

    override suspend fun run(): Boolean {
        if (!isActionSafe()) return true
        if (System.currentTimeMillis() >= deadline) return true
        if (FishingTrawler.inWreck(bot.position)) {
            val barrel = world.locator.findObjects(bot.position, 20) { it.id == 2159 || it.id == 2160 }.firstOrNull()
                ?: return true
            withTimeoutOrNull(15_000) { handler.interactions.interact(1, barrel) }
            return true
        }
        if (!FishingTrawler.atSea(bot.position)) {
            if (bot.catchRolls <= 0 && bot.unclaimedCatch.isEmpty()) return true
            return withTimeoutOrNull(120_000) {
                if (bot.inventory.isFull) {
                    if (!handler.banking.travelToBankOpen() || !handler.banking.depositInventory() ||
                        !handler.widgets.clickCloseInterface()) return@withTimeoutOrNull true
                }
                if (!handler.webWalkTo(FishingTrawler.DOCK, radius = 3)) return@withTimeoutOrNull true
                val net = world.locator.findObjects(FishingTrawler.DOCK, 25) { it.id == 2166 }.firstOrNull()
                    ?: return@withTimeoutOrNull true
                if (!handler.interactions.interact(1, net)) return@withTimeoutOrNull true
                delay(2.seconds)
                while (!bot.inventory.isFull && TrawlerCatchInterface::class in bot.overlays) {
                    val item = FishingTrawler.catchItems(bot).firstOrNull() ?: break
                    val before = bot.unclaimedCatch.size
                    if (!bot.output.sendItemWidgetClick(2, 0, 4640, item.id) ||
                        !waitFor(5.seconds) { bot.unclaimedCatch.size < before }) return@withTimeoutOrNull true
                }
                handler.widgets.clickCloseInterface()
                bot.catchRolls <= 0 && bot.unclaimedCatch.isEmpty()
            } ?: true
        }
        if (!handler.widgets.clickCloseInterface()) return true
        val objects = world.locator.findObjects(bot.position, 20) { true }
        val leak = objects.filter { it.id == 2167 }.minByOrNull { it.position.computeLongestDistance(bot.position) }
        val net = objects.filter { it.id == 2172 || it.id == 2173 }.minByOrNull { it.position.computeLongestDistance(bot.position) }
        when {
            bot.inventory.contains(585) -> handler.inventory.clickItem(1, 585)
            bot.position.z == 0 && FishingTrawler.waterLevel(bot) >= 80 && bot.inventory.contains(583) ->
                handler.inventory.clickItem(1, 583)
            leak != null && bot.inventory.contains(1941) -> withTimeoutOrNull(10_000) { handler.interactions.interact(1, leak) }
            bot.position.z == 0 && FishingTrawler.flooded(bot) && FishingTrawler.waterLevel(bot) > 0 && bot.inventory.contains(583) ->
                handler.inventory.clickItem(1, 583)
            net != null && bot.inventory.contains(954) -> withTimeoutOrNull(10_000) { handler.interactions.interact(1, net) }
            else -> {
                val ladderId = if (bot.position.z == 0) 2174 else 2175
                objects.firstOrNull { it.id == ladderId }?.let {
                    withTimeoutOrNull(10_000) { handler.interactions.interact(1, it) }
                }
            }
        }
        delay(2.seconds)
        return false
    }

    override fun paused() {
        FishingTrawler.abandon(bot)
        bot.navigator.cancel()
        bot.walking.clear()
    }

    override fun allowsLoot(item: GroundItem): Boolean = false

    override suspend fun finish() { paused() }

    override fun snapshot() = VoyageData().also { it.boarded = boarded; it.deadline = deadline }
}
