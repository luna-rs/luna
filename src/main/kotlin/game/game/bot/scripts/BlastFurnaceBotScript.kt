package game.bot.scripts

import api.bot.Suspendable.delay
import api.bot.Suspendable.waitFor
import api.bot.action.BotCombatActionHandler
import api.bot.action.BotSupplySources
import api.bot.isRealBot
import api.bot.zone.SubZone
import api.bot.script.BotScript
import api.bot.script.BotScriptData
import api.bot.script.ProductionPlan
import api.bot.action.trading.BotTradeIntentions
import api.bot.webwalk.plan.BankAccess
import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import com.google.gson.JsonObject
import engine.bot.coordinator.skill.SkillingScriptFactory
import engine.controllers.Controllers.inWilderness
import game.content.blastFurnace.BlastFurnace
import game.content.blastFurnace.BlastFurnaceInterface
import game.content.blastFurnace.BlastFurnaceWorkAction
import game.skill.smithing.BarType
import game.bot.scripts.BlastFurnaceCrew.Call
import game.bot.scripts.BlastFurnaceCrew.Role
import game.bot.scripts.trading.TradeBotScript
import io.luna.game.model.Direction
import io.luna.game.model.EntityState
import io.luna.game.model.EntityType
import io.luna.game.model.Position
import io.luna.game.model.item.GroundItem
import io.luna.game.model.item.Item
import io.luna.game.model.item.shop.Shop
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import io.luna.game.model.mob.dialogue.OptionDialogue
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.game.model.`object`.GameObject
import kotlinx.coroutines.withTimeoutOrNull
import java.util.ArrayDeque
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.milliseconds

/**
 * Repeats manual furnace batches using owned ore, coal, tools and fees through normal player interactions.
 * A missing bucket, spade or hammer is picked up from the room's own spawns.
 * A shared crew reserves real pump, pedal, stove and repair jobs; smelters own their ore, cooling and bars.
 * Loaded ore and earned bars belong to native saved stock, so a restored visit recovers them before adding supplies.
 *
 * Reaches Keldagrim through the native entrance, boat and stairs. The furnace room has no bank, so banking uses the
 * nearest reachable bank outside it, without a recovery teleport.
 * Without owned ore, loads are bought from Ordan in the room while bars wait in the dispenser until they fill an
 * inventory. Fees for the planned visit are carried and renewed at the foreman without banking.
 * Only smelters pay: the fee covers the belt and the bar dispenser, so workers neither carry nor renew it.
 * Consecutive failures, ten minutes without smelting progress and an absolute saved deadline bound the visit.
 *
 * @author lare96
 */
class BlastFurnaceBotScript(bot: Bot, val selectedBar: BarType = BarType.STEEL,
                            private var prepared: Boolean = false,
                            private var deadline: Long = System.currentTimeMillis() + 1_200_000,
                            private var failures: Int = 0,
                            val role: Role = Role.SMELTER,
                            private var supplyPlan: ProductionPlan? = null,
                            private var workDuration: Long = 0L,
                            private var shopPurchaseDeadline: Long = 0L) : BotScript(bot) {
    companion object {
        private const val TRAVEL_MILLIS = 20 * 60_000L
        private const val PURCHASE_MILLIS = 120_000L
        private const val PREPARATION_MILLIS = 30 * 60_000L

        /**
         * Longest stint in one worker job, so crew jobs rotate. A chosen value: Jagex 2006 asks players to swap
         * roles from time to time without naming a length. Smelters keep their normal session length.
         */
        private const val CREW_STINT_MILLIS = 15 * 60_000L

        /**
         * Temperature gauge object, its tile and the interface its "Read" option opens.
         */
        private const val GAUGE_OBJECT = 9089
        private const val GAUGE_INTERFACE = 14594
        private val GAUGE = Position(1945, 4961)

        /**
         * Ticks between idle gauge readings while nobody pumps, and between readings while someone does.
         * Chosen values: heat falls one degree every five ticks and rises four each pumping tick.
         */
        private const val WATCH_TICKS = 15L
        private const val PUMPING_WATCH_TICKS = 2L

        /**
         * Longest a reader keeps watching one pumping session before going back to its own work. A chosen value.
         */
        private const val WATCH_LIMIT_TICKS = 30L

        /**
         * Shortest and longest pumping burst, in ticks, for a pumper with no gauge reader. A chosen range wide
         * enough that a burst sometimes overheats the furnace.
         */
        private const val BURST_MIN_TICKS = 4
        private const val BURST_MAX_TICKS = 8
        private var Bot.nextAttempt by Attr.long()

        private fun defer(bot: Bot) { bot.nextAttempt = System.currentTimeMillis() + 15 * 60_000L }

        private fun owned(bot: Bot, id: Int): Long {
            val note = itemDef(id).notedId.orElse(-1)
            return bot.inventory.computeAmountForId(id).toLong() + bot.bank.computeAmountForId(id) +
                if (note >= 0) bot.inventory.computeAmountForId(note).toLong() else 0L
        }

        private fun canWork(bot: Bot, role: Role): Boolean =
            BotCombatActionHandler.mayGainExperience(bot, role.skillId) &&
                (role == Role.SMELTER || bot.skill(role.skillId).staticLevel >= 30 && bot.skill(role.skillId).level >= 30)

        private fun available(bot: Bot): Boolean = bot.state == EntityState.ACTIVE &&
            ActionSafety.NON_COMBAT.allows(bot) && !bot.inWilderness() &&
            System.currentTimeMillis() >= bot.nextAttempt

        /**
         * Lists the tools this bot already owns for its job and for any station it can cover.
         * Unowned tools are left to the room's bucket, spade and hammer spawns.
         */
        private fun tools(bot: Bot, role: Role): List<Item> {
            val bucket = if (owned(bot, BlastFurnace.WATER) > 0) BlastFurnace.WATER else BlastFurnace.BUCKET
            val spade = if (owned(bot, BlastFurnace.COKE_SPADE) > 0) BlastFurnace.COKE_SPADE else BlastFurnace.SPADE
            return listOfNotNull(bucket.takeIf { role == Role.SMELTER },
                spade.takeIf { role == Role.STOVE || canWork(bot, Role.STOVE) },
                BlastFurnace.HAMMER.takeIf { role == Role.REPAIR || canWork(bot, Role.REPAIR) })
                .filter { owned(bot, it) > 0L }.map { Item(it) }
        }

        /**
         * Counts the fees still owed for [millis] more furnace use below 60 Smithing, after access already paid for.
         */
        private fun fees(bot: Bot, millis: Long): Int {
            if (bot.smithing.staticLevel >= 60 || millis <= 0L) return 0
            val now = System.currentTimeMillis()
            val paid = (1..(BlastFurnace.ACCESS_MILLIS / 60_000L).toInt())
                .count { BlastFurnace.hasAccess(bot, now + it * 60_000L) } * 60_000L
            val unpaid = millis - paid
            return if (unpaid <= 0L) 0 else ((unpaid + BlastFurnace.ACCESS_MILLIS - 1) / BlastFurnace.ACCESS_MILLIS).toInt()
        }

        /**
         * Fills an active furnace's vacant job at a normal idle decision boundary: a repair first, then the stove,
         * pump and pedals, none of which the furnace can run without. The name is kept for its existing caller.
         */
        @JvmStatic
        fun repairCandidate(bot: Bot): BlastFurnaceBotScript? {
            if (!bot.isRealBot() || !available(bot)) return null
            for (role in listOf(Role.REPAIR, Role.STOVE, Role.PUMP, Role.PEDALS)) {
                if (canWork(bot, role) && BlastFurnaceCrew.vacant(bot, role))
                    select(bot, role.skillId)?.let { return it }
            }
            return null
        }

        /**
         * Reserves missing crew jobs before travel, or admits an ore owner for a normal Smithing session.
         * A first smelter may arrive before its crew so an empty furnace can assemble without a circular gate.
         * A worker job lasts at most [CREW_STINT_MILLIS], a chosen cap, so the crew swaps jobs over time.
         */
        fun select(bot: Bot, skillId: Int? = null, durationMillis: Long = 0L): BlastFurnaceBotScript? {
            if (!available(bot)) return null
            val roles = Role.entries.filter { skillId == null || it.skillId == skillId }
                .sortedBy { when (it) { Role.REPAIR -> 0; Role.SMELTER -> 2; else -> 1 } }
            for (role in roles) {
                if (!canWork(bot, role) || role != Role.SMELTER && BlastFurnaceCrew.occupied(bot, role)) continue
                if (role == Role.SMELTER && !BlastFurnace.hasAccess(bot) && owned(bot, 995) < BlastFurnace.FEE) continue
                val session = if (durationMillis > 0L) durationMillis
                    else SkillingScriptFactory.getDuration(bot, role.skillId).inWholeMilliseconds
                val duration = if (role == Role.SMELTER) session else minOf(session, CREW_STINT_MILLIS)
                val bar = if (role == Role.SMELTER) {
                    val choices = BarType.entries.filter { it.level <= bot.smithing.staticLevel && it.level <= bot.smithing.level }
                        .sortedByDescending { it.level }
                    choices.firstOrNull { supplies(bot, it, !pendingWork(bot), visitMillis = duration) != null }
                        ?: choices.firstOrNull { eligible(bot, it) } ?: continue
                } else BarType.STEEL
                val script = BlastFurnaceBotScript(bot, bar,
                    deadline = System.currentTimeMillis() + PREPARATION_MILLIS, role = role,
                    workDuration = duration)
                if (BlastFurnaceCrew.claim(bot, script, role, script.deadline)) {
                    bot.log("Blast Furnace: reserved $role.")
                    return script
                }
            }
            return null
        }

        private fun pendingWork(bot: Bot): Boolean = BlastFurnace.totalBars(bot) > 0 || oreWork(bot)

        /**
         * Reports ore still moving on the belt or enough loaded ore for another bar.
         */
        private fun oreWork(bot: Bot): Boolean = BlastFurnace.beltCount(bot) > 0 ||
            BarType.entries.any { bar -> bar.level <= bot.smithing.level &&
                BlastFurnace.ingredients(bar).all { BlastFurnace.oreCount(bot, it.id) >= it.amount } }

        /**
         * Reserves owned tools and the fees for [visitMillis] of furnace use, then fills the remaining inventory with
         * a complete recipe. Existing furnace stock contributes to the recipe; depositing ore frees room for its bars.
         * Without a complete owned recipe, coins for the loads one inventory of bars allows, plus one spare, are
         * carried for the room's ore seller instead.
         */
        fun supplies(bot: Bot, bar: BarType, includeOre: Boolean = true, role: Role = Role.SMELTER,
                     visitMillis: Long = BlastFurnace.ACCESS_MILLIS): List<Item>? {
            if (role == Role.SMELTER && bot.smithing.staticLevel < bar.level) return null
            val supplies = tools(bot, role).toMutableList()
            val coins = owned(bot, 995)
            if (role == Role.SMELTER && !BlastFurnace.hasAccess(bot) && coins < BlastFurnace.FEE) return null
            var carried = if (role != Role.SMELTER) 0L
                else minOf(fees(bot, visitMillis).toLong(), coins / BlastFurnace.FEE) * BlastFurnace.FEE
            val ore = mutableListOf<Item>()
            if (includeOre && role == Role.SMELTER) {
                val recipe = BlastFurnace.ingredients(bar)
                val slots = bot.inventory.capacity() - supplies.size - if (carried > 0L) 1 else 0
                val maximum = recipe.minOf {
                    minOf(255L / it.amount, (owned(bot, it.id) + BlastFurnace.oreCount(bot, it.id)) / it.amount).toInt()
                }
                val batches = (maximum downTo 1).firstOrNull { count ->
                    recipe.sumOf { (it.amount * count - BlastFurnace.oreCount(bot, it.id)).coerceAtLeast(0) } <= slots
                }
                if (batches != null) ore += recipe.mapNotNull {
                    val amount = (it.amount * batches - BlastFurnace.oreCount(bot, it.id)).coerceAtLeast(0)
                    if (amount > 0) Item(it.id, amount) else null
                } else {
                    val shop = BlastFurnaceSupplies.seller(bot, bar) ?: return null
                    val barSlots = bot.inventory.capacity() - supplies.size - 1
                    val load = BlastFurnaceSupplies.sellerLoad(bot, bar, shop, barSlots, coins - carried) ?: return null
                    carried += minOf(coins - carried, load.coins * ((barSlots / load.bars).coerceAtLeast(1) + 1))
                }
            }
            if (carried > 0L) supplies += Item(995, carried.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            return supplies + ore
        }

        fun eligible(bot: Bot): Boolean = eligible(bot, BarType.STEEL)
        fun eligible(bot: Bot, bar: BarType): Boolean = available(bot) && canWork(bot, Role.SMELTER) &&
            bar.level <= bot.smithing.level && bar.level <= bot.smithing.staticLevel &&
            (supplies(bot, bar, !pendingWork(bot)) != null ||
                BlastFurnaceSupplies.create(bot, bar, tools(bot, Role.SMELTER),
                    System.currentTimeMillis() + 120_000L) != null)
    }

    /**
     * Retains batch choice and bounded visit state; native stock is persisted independently by the furnace.
     *
     * @author lare96
     */
    class FurnaceData : BotScriptData() {
        var bar = BarType.STEEL
        var prepared = false
        var deadline = 0L
        var failures = 0
        var role = Role.SMELTER
        var supplyPlan: ProductionPlan? = null
        var workDuration = 0L
        var shopPurchaseDeadline = 0L
        override fun load(data: JsonObject) {
            bar = BarType.valueOf(data.get("bar").asString)
            prepared = data.get("prepared").asBoolean
            deadline = data.get("deadline").asLong
            failures = data.get("failures")?.asInt ?: 0
            role = data.get("role")?.asString?.let { runCatching { Role.valueOf(it) }.getOrNull() } ?: Role.SMELTER
            supplyPlan = data.getAsJsonObject("supplies")?.let(ProductionPlan::load)
            workDuration = data.get("work_duration")?.asLong ?: 0L
            shopPurchaseDeadline = data.get("shop_purchase_deadline")?.asLong ?: 0L
        }
        override fun save(data: JsonObject) {
            data.addProperty("bar", bar.name)
            data.addProperty("prepared", prepared)
            data.addProperty("deadline", deadline)
            data.addProperty("failures", failures)
            data.addProperty("role", role.name)
            supplyPlan?.let { data.add("supplies", it.save()) }
            data.addProperty("work_duration", workDuration)
            data.addProperty("shop_purchase_deadline", shopPurchaseDeadline)
        }
    }

    constructor(bot: Bot, data: FurnaceData) : this(bot, data.bar, data.prepared, data.deadline, data.failures,
        data.role, data.supplyPlan, data.workDuration, data.shopPurchaseDeadline)
    private var lastProgress = System.currentTimeMillis()
    private var previous: List<Any?>? = null
    override val allowsCombatResponse = false
    private var roomTool = -1
    private var pumpedUntil = 0L
    override fun allowsLoot(item: GroundItem): Boolean = item.id == roomTool && BlastFurnace.inside(item.position)

    private fun valid(): Boolean = System.currentTimeMillis() < deadline && isActionSafe() &&
        BlastFurnace.present(bot) && canWork(bot, role)

    private fun visitMillis(): Long = if (workDuration > 0L) workDuration else deadline - System.currentTimeMillis()

    private fun plan(includeOre: Boolean = true): List<Item>? =
        supplies(bot, selectedBar, includeOre, role, visitMillis())

    /**
     * Returns the room's ore seller while this smelter has no complete recipe left in its bank or as notes.
     */
    private fun seller(): Shop? {
        if (role != Role.SMELTER || trainingComplete() || BlastFurnace.ingredients(selectedBar)
                .all { owned(bot, it.id) - bot.inventory.computeAmountForId(it.id) >= it.amount }) return null
        return BlastFurnaceSupplies.seller(bot, selectedBar)
    }

    /**
     * Slots left for bars once the carried tools, the cooling bucket and coins are counted.
     */
    private fun barSlots(): Int {
        val tools = tools(bot, role).map { it.id }
        return bot.inventory.capacity() - tools.size - 1 -
            if (BlastFurnace.BUCKET in tools || BlastFurnace.WATER in tools) 0 else 1
    }

    private fun carriesBars(): Boolean = BarType.entries.any { bot.inventory.contains(it.id) }

    /**
     * Coins carried beyond the fees the rest of the visit needs.
     */
    private fun spendable(): Long = bot.inventory.computeAmountForId(995).toLong() -
        fees(bot, deadline - System.currentTimeMillis()).toLong() * BlastFurnace.FEE

    /**
     * Prices the next load from spendable coins. Bars already in the dispenser and the next load's bars must still
     * fit one inventory, so the later bank trip carries them all.
     */
    private fun nextLoad(shop: Shop): BlastFurnaceSupplies.SellerLoad? {
        if (carriesBars()) return null
        return BlastFurnaceSupplies.sellerLoad(bot, selectedBar, shop, bot.inventory.computeRemainingSize(), spendable())
            ?.takeIf { BlastFurnace.totalBars(bot) + it.bars <= barSlots() }
    }

    /**
     * Buys one load from Ordan through his normal shop. A partial purchase still goes on the belt and the
     * next load completes its recipe; stock bought by another player first ends in the normal restock.
     */
    private suspend fun buyLoad(shop: Shop, load: BlastFurnaceSupplies.SellerLoad): Boolean {
        val coins = bot.inventory.computeAmountForId(995)
        var bought = false
        if (handler.shop.travelToShop(shop) { valid() } != null) {
            for ((id, amount) in load.ore) {
                val target = bot.inventory.computeAmountForId(id) + amount
                while (bot.inventory.computeAmountForId(id) < target && handler.shop.canTradeWith(shop) &&
                    handler.shop.buyUpTo(shop, id, target - bot.inventory.computeAmountForId(id),
                        spendable().coerceIn(0L, Int.MAX_VALUE.toLong()).toInt())) bought = true
            }
            handler.widgets.clickCloseInterface()
        }
        bot.log("Blast Furnace: bought ${load.ore} from Ordan for ${coins - bot.inventory.computeAmountForId(995)} coins.")
        failures = if (bought) 0 else failures + 1
        return false
    }

    private fun hasShopSupply(targets: Map<Int, Int>, budget: Int, prices: Map<Int, Long>): Boolean =
        budget > 0 && targets.any { (id, amount) -> owned(bot, id) < amount &&
            BotSupplySources.quote(bot, id, 1)?.let {
                it.totalCoins <= budget && it.maximumUnitPrice <= (prices[id] ?: 0L)
            } == true }

    private fun spendingBudget(maximum: Long): Int = minOf(maximum,
        (owned(bot, 995) - if (bot.smithing.staticLevel >= 60) 0 else BlastFurnace.FEE).coerceAtLeast(0L))
        .coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    /**
     * Travels and stages purchase funds before the short shop-interaction clock starts.
     * The caller's saved absolute travel deadline prevents pauses or restarts extending the trip.
     */
    private suspend fun stageShop(targets: Map<Int, Int>, budget: Int, prices: Map<Int, Long>,
                                  travelDeadline: Long): Boolean {
        if (!hasShopSupply(targets, budget, prices)) return false
        val remaining = minOf(deadline, travelDeadline) - System.currentTimeMillis()
        if (remaining <= 0L) return false
        BlastFurnaceCrew.refresh(bot, this, travelling = true)
        prepared = false
        return withTimeoutOrNull(remaining) {
            if (!isActionSafe() || !handler.webWalkTo(SubZone.HOME, radius = 2, attempts = 2,
                    bank = BankAccess.NONE, fallback = false)) return@withTimeoutOrNull false
            val funds = spendingBudget(budget.toLong())
            funds > 0 && bank(listOf(Item(995, funds)))
        } == true
    }

    /**
     * Purchases one finite ore basket through normal trades and stocked shops before committing to travel.
     * The same saved target, actual-spend allowance and absolute deadline survive child trades and restarts.
     */
    private suspend fun prepareSupplies(): Boolean {
        val ids = tools(bot, role).map { it.id } +
            if (role == Role.SMELTER) BlastFurnace.ingredients(selectedBar).map { it.id } else emptyList()
        if (ids.any { id -> itemDef(id).notedId.orElse(-1).let { it >= 0 && bot.inventory.contains(it) } }) {
            if (!bank(emptyList())) return false
            prepared = false
        }
        if (role != Role.SMELTER || pendingWork()) return retireSupplies()
        val purchase = supplyPlan
        if (purchase == null && plan() != null) return true
        val acquisition = purchase ?: BlastFurnaceSupplies.create(bot, selectedBar, tools(bot, role),
            minOf(deadline, System.currentTimeMillis() + TRAVEL_MILLIS + 2 * PURCHASE_MILLIS))
            ?.also { supplyPlan = it } ?: return false
        if (acquisition.supplyDeadline == 0L)
            acquisition.supplyDeadline = minOf(acquisition.deadline, System.currentTimeMillis() + PURCHASE_MILLIS)
        BotTradeIntentions.recordProductionPlan(bot, acquisition, this)
        fun supplied() = plan() != null
        if (!supplied() && System.currentTimeMillis() < acquisition.supplyDeadline) {
            for ((id, target) in acquisition.targets()) {
                if (owned(bot, id) >= target || !acquisition.attemptedMarketItems.add(id)) continue
                val remaining = acquisition.supplyDeadline - System.currentTimeMillis()
                if (remaining <= 0L) break
                val trade = TradeBotScript.buySpecific(bot, id, target, acquisition.supplyDeadline) ?: continue
                trade.duration = minOf(30_000L, remaining).milliseconds
                prepared = false
                bot.scriptStack.pushHead(trade)
                return false
            }
        }
        if (!acquisition.shopAttempted && !supplied() && System.currentTimeMillis() < acquisition.deadline &&
            hasShopSupply(acquisition.targets(), (acquisition.maximumCoins - acquisition.spentCoins)
                .coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
                acquisition.maximumUnitPrices)) {
            val budget = acquisition.remainingCoins.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            if (stageShop(acquisition.targets(), budget, acquisition.maximumUnitPrices, acquisition.deadline)) {
                if (shopPurchaseDeadline == 0L)
                    shopPurchaseDeadline = minOf(acquisition.deadline, System.currentTimeMillis() + PURCHASE_MILLIS)
                val remaining = shopPurchaseDeadline - System.currentTimeMillis()
                if (remaining > 0L) withTimeoutOrNull(remaining) {
                    handler.shop.obtainWantedItems(acquisition.targets(), spendingBudget(acquisition.remainingCoins),
                        acquisition.maximumUnitPrices)
                }
            }
            acquisition.shopAttempted = true
        }
        if (!supplied()) bot.log("Blast Furnace: $role supply acquisition ended without a complete recipe.")
        return retireSupplies() && supplied()
    }

    private suspend fun retireSupplies(): Boolean {
        val acquisition = supplyPlan ?: return true
        BotTradeIntentions.clearProductionPlan(bot, acquisition, this)
        supplyPlan = null
        shopPurchaseDeadline = 0L
        return true
    }

    private fun pendingWork(): Boolean = pendingWork(bot)

    private fun carriedOre(): Boolean = BlastFurnace.ingredients(selectedBar).any { bot.inventory.contains(it.id) }

    override suspend fun init(resumed: Boolean): Boolean {
        if (System.currentTimeMillis() >= deadline || failures >= 5 || !available(bot) || !canWork(bot, role) ||
            !BlastFurnaceCrew.claim(bot, this, role, deadline)) return true
        BlastFurnaceCrew.refresh(bot, this)
        val finished = withTimeoutOrNull((deadline - System.currentTimeMillis()).coerceAtLeast(1L)) {
            if (!prepareSupplies()) return@withTimeoutOrNull true
            if (!prepared || tools(bot, role).any { !bot.inventory.contains(it.id) }) {
                val plan = plan(!pendingWork()) ?: return@withTimeoutOrNull true
                if (!bot.inventory.containsAll(plan) && !bank(plan)) return@withTimeoutOrNull true
                prepared = true
            }
            withTimeoutOrNull(minOf(TRAVEL_MILLIS, (deadline - System.currentTimeMillis()).coerceAtLeast(1L))) {
                !returnToFurnace()
            } ?: true
        } ?: true
        if (finished) { if (!isPaused()) defer(bot) }
        else {
            if (workDuration > 0L) { deadline = System.currentTimeMillis() + workDuration; workDuration = 0L }
            if (!BlastFurnaceCrew.claim(bot, this, role, deadline)) return true
            resetProgress()
            bot.log("Blast Furnace: arrived as $role.")
        }
        return finished
    }

    private fun find(position: Position, vararg ids: Int): GameObject? =
        world.locator.findObjects(position, 0) { it.id in ids }.firstOrNull()

    private suspend fun click(position: Position, vararg ids: Int): Boolean {
        val obj = find(position, *ids) ?: return false
        return handler.interactions.approach(obj, timeout = 12.seconds, allowOutOfView = true) { valid() } &&
            handler.interactions.interact(1, obj) { valid() }
    }

    private fun operating(): Boolean = bot.actions.contains(BlastFurnaceWorkAction::class.java)

    /**
     * Stops a pump or pedal shift the way a player does, by walking one tile off the station. A click on another
     * object interrupts the shift by itself, so this is only for a bot that just needs to stop.
     */
    private suspend fun stepOff() {
        if (!operating()) return
        val from = bot.position
        val step = Direction.NESW.shuffled().firstOrNull {
            world.collisionManager.traversable(from, EntityType.PLAYER, it)
        }?.let { from.translate(1, it) } ?: return
        bot.walking.replacePath(ArrayDeque(listOf(step)))
        waitFor(3.seconds) { !operating() }
    }

    private fun gaugeOpen(): Boolean =
        bot.overlays.getOverlay(StandardInterface::class.java)?.id == GAUGE_INTERFACE

    /**
     * Reads the Temperature gauge and calls the reading to the crew. While someone pumps, the reader keeps
     * watching so the stop call comes in time; the pumper acts only on what was read.
     */
    private suspend fun readGauge(): Boolean {
        if (!BlastFurnaceCrew.claimReading(bot)) return true
        val start = world.currentTick
        while (true) {
            if (!click(GAUGE, GAUGE_OBJECT) || !waitFor(5.seconds) { gaugeOpen() }) return false
            val heard = BlastFurnaceCrew.read(bot, BlastFurnace.temperature(bot))
            bot.log("Blast Furnace: gauge read ${heard.temperature}, call ${heard.call}.")
            handler.widgets.clickCloseInterface()
            if (heard.call != Call.PUMP || BlastFurnace.operator(bot, BlastFurnaceWorkAction.Role.PUMP) == null ||
                world.currentTick - start >= WATCH_LIMIT_TICKS || !valid()) return true
        }
    }

    /**
     * Whether this bot or a smelter in the room has ore to smelt or bars to take.
     */
    private fun furnaceInUse(): Boolean = pendingWork() || BlastFurnaceCrew.members(bot).any {
        it.role == Role.SMELTER && it.arrived && pendingWork(it.bot)
    }

    /**
     * Whether this bot should check the gauge now: a furnace in use with no recent reading, more often while
     * someone pumps.
     */
    private fun gaugeDue(): Boolean {
        val interval = if (BlastFurnace.operator(bot, BlastFurnaceWorkAction.Role.PUMP) != null)
            PUMPING_WATCH_TICKS else WATCH_TICKS
        return furnaceInUse() && BlastFurnaceCrew.call(bot, interval - 1) == null
    }

    /**
     * Pumps on a fresh "pump" call until the reader calls stop. A pumper with no reader pumps one short burst and
     * then checks the gauge itself, so a long burst can overheat the furnace.
     */
    private suspend fun pump(): Boolean {
        val start = world.currentTick
        val burst = rand(BURST_MIN_TICKS, BURST_MAX_TICKS)
        val result = click(BlastFurnace.PUMP, 9090) && waitFor(3.seconds) { operating() } && waitFor(20.seconds) {
            !valid() || !operating() || BlastFurnace.damage(bot) != null ||
                BlastFurnaceCrew.call(bot)?.let { it.tick > start && it.call != Call.PUMP } == true ||
                !BlastFurnaceCrew.readerNearby(bot) && world.currentTick - start >= burst
        }
        pumpedUntil = world.currentTick
        stepOff()
        return result
    }

    private fun observe() {
        val state = listOf(BlastFurnace.totalOre(bot), BlastFurnace.totalBars(bot), BlastFurnace.beltCount(bot),
            BlastFurnace.hot(bot))
        if (state != previous) { previous = state; lastProgress = System.currentTimeMillis() }
    }

    override suspend fun run(): Boolean {
        if (!BlastFurnaceCrew.claim(bot, this, role, deadline)) return finishBanking()
        BlastFurnaceCrew.refresh(bot, this)
        if (!valid() || failures >= 5) {
            if (failures >= 5) defer(bot)
            return finishBanking()
        }
        if (role == Role.SMELTER && !BlastFurnace.hasAccess(bot)) {
            val plan = if (bot.inventory.computeAmountForId(995) >= BlastFurnace.FEE) null else plan(false)
            val renewed = (plan != null || bot.inventory.computeAmountForId(995) >= BlastFurnace.FEE) &&
                withTimeoutOrNull(minOf(TRAVEL_MILLIS, (deadline - System.currentTimeMillis()).coerceAtLeast(1L))) {
                    (plan == null || bank(plan)) && returnToFurnace()
                } == true
            if (!renewed) { defer(bot); return true }
            resetProgress()
        }
        if (role != Role.SMELTER) {
            val success = withTimeoutOrNull(30_000) { maintain() } == true
            failures = if (success) 0 else failures + 1
            delay(2.seconds)
            return false
        }
        observe()
        if (System.currentTimeMillis() - lastProgress >= 600_000) {
            bot.log("Blast Furnace: no progress while waiting for crew.")
            defer(bot)
            return finishBanking()
        }
        if (!carriedOre() && !oreWork(bot)) {
            val shop = seller()
            val load = shop?.let { nextLoad(it) }
            if (shop != null && load != null) return buyLoad(shop, load)
            if (BlastFurnace.totalBars(bot) == 0) return restock()
        }
        val success = withTimeoutOrNull(if (bot.inventory.isFull && BlastFurnace.totalBars(bot) > 0)
                minOf(TRAVEL_MILLIS, (deadline - System.currentTimeMillis()).coerceAtLeast(1L)) else 30_000) {
            cycle()
        } == true
        failures = if (success) 0 else failures + 1
        delay(1.seconds)
        return false
    }

    private fun resetProgress() {
        previous = null
        lastProgress = System.currentTimeMillis()
    }

    private suspend fun restock(): Boolean {
        prepared = false
        if (!trainingComplete() && !prepareSupplies()) {
            if (isPaused() || bot.scriptStack.current() !== this) return false
            defer(bot)
            return finishBanking()
        }
        val plan = if (trainingComplete()) null else plan()
        if (plan == null) return finishBanking()
        val stocked = !carriesBars() && bot.inventory.containsAll(plan)
        if (stocked && seller() != null) {
            bot.log("Blast Furnace: Ordan cannot supply another load.")
            defer(bot)
            return finishBanking()
        }
        val ready = withTimeoutOrNull(minOf(TRAVEL_MILLIS,
            (deadline - System.currentTimeMillis()).coerceAtLeast(1L))) { (stocked || bank(plan)) && returnToFurnace() } == true
        if (!ready) { defer(bot); return true }
        prepared = true
        resetProgress()
        return false
    }

    private suspend fun finishBanking(): Boolean {
        BlastFurnaceCrew.release(bot, this)
        retireSupplies()
        if (bot.state == EntityState.ACTIVE && isActionSafe() && !bot.inWilderness()) {
            withTimeoutOrNull(120_000) { bank(emptyList(), ending = true) }
        }
        return true
    }

    private suspend fun returnToFurnace(): Boolean {
        BlastFurnaceCrew.refresh(bot, this, travelling = true)
        if (!handler.webWalkTo(BlastFurnace.ENTRY, radius = 3, attempts = 2,
                bank = BankAccess.NONE, fallback = false) || !valid()) return false
        if (role != Role.SMELTER || BlastFurnace.hasAccess(bot)) return true
        if (bot.inventory.computeAmountForId(995) < BlastFurnace.FEE) {
            val plan = plan(false) ?: return false
            if (!bank(plan) || !handler.webWalkTo(BlastFurnace.ENTRY, radius = 3, attempts = 2,
                    bank = BankAccess.NONE, fallback = false) || !valid()) return false
        }
        val foreman = world.locator.findNpcs(BlastFurnace.FOREMAN, 6) { it.id == 2553 }.firstOrNull() ?: return false
        if (!handler.interactions.approach(foreman, timeout = 10.seconds, allowOutOfView = true) { valid() }) return false
        // Stepping off the foreman's tile after the click closes his options, so one more click is allowed.
        val talking = (1..2).any {
            handler.interactions.interact(1, foreman) { valid() } &&
                waitFor(5.seconds) { bot.overlays.has(OptionDialogue::class.java) }
        }
        if (!talking || !handler.widgets.clickDialogueOption(1) || !waitFor(5.seconds) { BlastFurnace.hasAccess(bot) })
            return false
        return handler.widgets.clickCloseInterface() && valid()
    }

    /**
     * Picks up a tool lying in the furnace room. A taken spawn is a normal wait for its respawn.
     */
    private suspend fun takeRoomTool(id: Int): Boolean {
        val tool = world.locator.findItems(bot.position, 32) {
            it.id == id && it.state == EntityState.ACTIVE && it.isVisibleTo(bot) && BlastFurnace.inside(it.position)
        }.minByOrNull { bot.position.computeLongestDistance(it.position) } ?: return false
        roomTool = id
        try { return handler.loot.pickup(tool) { valid() } } finally { roomTool = -1 }
    }

    private suspend fun cycle(): Boolean {
        if (seller() != null) {
            // Between Ordan loads, bars wait in the dispenser; taking them early would fill the inventory.
            if (carriedOre()) return loadBelt()
            if (oreWork(bot)) return maintain()
        }
        if (BlastFurnace.hot(bot)) {
            if (!bot.inventory.containsAny(BlastFurnace.WATER, BlastFurnace.BUCKET) &&
                !takeRoomTool(BlastFurnace.BUCKET)) return maintain()
            if (!bot.inventory.contains(BlastFurnace.WATER) && !fillCoolingBucket()) return false
            val dispenser = find(BlastFurnace.DISPENSER, 9092, 9095) ?: return false
            return handler.inventory.useItem(BlastFurnace.WATER).onObject(dispenser) &&
                waitFor(5.seconds) { !BlastFurnace.hot(bot) }
        }
        if (BlastFurnace.totalBars(bot) > 0) {
            if (bot.inventory.isFull) {
                // Saved mixed stock can exceed one inventory. Preserve it and make as many real bank trips
                // as the saved visit budget allows; never withdraw another ore batch during claim recovery.
                val plan = (if (seller() != null) plan() else null) ?: plan(false) ?: return false
                return bank(plan) && returnToFurnace()
            }
            if (!click(BlastFurnace.DISPENSER, 9092, 9093, 9096) ||
                !waitFor(5.seconds) { bot.overlays.has(BlastFurnaceInterface::class.java) }) return false
            val bar = BlastFurnaceInterface.order.first { BlastFurnace.barCount(bot, it) > 0 }
            val before = BlastFurnace.barCount(bot, bar)
            val result = handler.widgets.clickButton(BlastFurnaceInterface.ID, BlastFurnaceInterface.allButton(bar)) &&
                waitFor(5.seconds) { BlastFurnace.barCount(bot, bar) < before }
            handler.widgets.clickCloseInterface()
            return result
        }
        if (carriedOre()) return loadBelt()
        return maintain()
    }

    private suspend fun loadBelt(): Boolean {
        val before = BlastFurnace.totalOre(bot)
        return click(BlastFurnace.BELT, 9100) && waitFor(5.seconds) { BlastFurnace.totalOre(bot) > before }
    }

    private suspend fun fillCoolingBucket(): Boolean {
        if (!click(BlastFurnace.SINK, 9143)) return false
        if (!waitFor(5.seconds) {
                bot.inventory.contains(BlastFurnace.WATER) || bot.overlays.has(MakeItemDialogue::class.java)
            }) return false
        if (bot.inventory.contains(BlastFurnace.WATER)) return true
        handler.widgets.clickMakeItem(0, 1)
        return waitFor(5.seconds) { bot.inventory.contains(BlastFurnace.WATER) }
    }

    private fun handles(work: Role): Boolean = canWork(bot, work) &&
        (role == work || !BlastFurnaceCrew.occupied(bot, work, arrivedOnly = true))

    /**
     * Fills a reserved station, or assists an absent worker using only skills and tools actually held.
     * Empty belts, a hot-enough furnace and another operator are normal waiting states.
     * Temperature is known only from gauge readings; the stove's fuel level is visible on the stove itself, so
     * stove decisions read it directly. A bot with nothing else to do watches the gauge for the crew.
     */
    private suspend fun maintain(): Boolean {
        val damage = BlastFurnace.damage(bot)
        if (handles(Role.REPAIR) && (role == Role.REPAIR || damage != null) &&
            !bot.inventory.contains(BlastFurnace.HAMMER) && takeRoomTool(BlastFurnace.HAMMER)) return true
        if (handles(Role.STOVE) && (role == Role.STOVE || BlastFurnace.fuel(bot) < 120) &&
            !bot.inventory.containsAny(BlastFurnace.SPADE, BlastFurnace.COKE_SPADE) &&
            takeRoomTool(BlastFurnace.SPADE)) return true
        if (damage != null) {
            if (handles(Role.REPAIR) && bot.inventory.contains(BlastFurnace.HAMMER))
                return click(damage.position, damage.broken) &&
                    waitFor(5.seconds) { BlastFurnace.damage(bot) != damage }
            return true
        }
        if (BlastFurnace.fuel(bot) < 120 && handles(Role.STOVE) &&
            bot.inventory.containsAny(BlastFurnace.SPADE, BlastFurnace.COKE_SPADE)) {
            if (!bot.inventory.contains(BlastFurnace.COKE_SPADE) &&
                (!click(BlastFurnace.COKE, 9088) || !waitFor(5.seconds) { bot.inventory.contains(BlastFurnace.COKE_SPADE) }))
                return false
            val before = BlastFurnace.fuel(bot)
            return click(BlastFurnace.STOVE, 9085, 9086, 9087) && waitFor(5.seconds) { BlastFurnace.fuel(bot) > before }
        }
        if (handles(Role.PUMP) && BlastFurnace.fuel(bot) > 0 &&
            BlastFurnace.operator(bot, BlastFurnaceWorkAction.Role.PUMP).let { it == null || it === bot }) {
            val heard = BlastFurnaceCrew.call(bot)?.takeIf { it.tick > pumpedUntil }
            if (heard?.call == Call.PUMP) return pump()
            if (heard == null && !BlastFurnaceCrew.readerNearby(bot) && furnaceInUse()) return readGauge()
        }
        if (handles(Role.PEDALS) && BlastFurnace.hasBeltWork(bot) &&
            BlastFurnace.operator(bot, BlastFurnaceWorkAction.Role.PEDALS).let { it == null || it === bot }) {
            val started = click(BlastFurnace.PEDALS, 9097)
            val result = started && waitFor(15.seconds) {
                !valid() || !BlastFurnace.hasBeltWork(bot) || BlastFurnace.damage(bot) != null
            }
            stepOff()
            return result
        }
        if (gaugeDue()) return readGauge()
        delay(1.seconds)
        return true
    }

    /**
     * Consolidates owned supplies at a native bank without the banking handler's home/recovery fallback.
     * The actual second-click bank handler must acknowledge opening before deposits and withdrawals proceed.
     */
    private suspend fun bank(plan: List<Item>, ending: Boolean = false): Boolean {
        BlastFurnaceCrew.refresh(bot, this, travelling = true)
        val bank = handler.webWalkToNearestBank(fallback = false) ?: return false
        if ((!ending && System.currentTimeMillis() >= deadline) || bot.inWilderness() || !isActionSafe()) return false
        if (!handler.interactions.interact(2, bank) || !waitFor(5.seconds) { bot.bank.isOpen } ||
            !handler.banking.depositInventory()) return false
        for (item in plan) if (!handler.banking.withdrawUnnoted(item)) return false
        return handler.widgets.clickCloseInterface() && bot.inventory.containsAll(plan)
    }

    override suspend fun finish() {
        if (!isPaused()) supplyPlan?.let { BotTradeIntentions.clearProductionPlan(bot, it, this) }
        BlastFurnaceCrew.release(bot, this)
        if (bot.overlays.hasWindow()) output.sendCloseInterface()
        bot.actions.interruptWeak()
        bot.navigator.cancel()
        bot.walking.clear()
    }
    override fun snapshot() = FurnaceData().also {
        it.bar = selectedBar; it.prepared = prepared; it.deadline = deadline; it.failures = failures; it.role = role
        it.supplyPlan = supplyPlan
        it.workDuration = workDuration
        it.shopPurchaseDeadline = shopPurchaseDeadline
    }
}
