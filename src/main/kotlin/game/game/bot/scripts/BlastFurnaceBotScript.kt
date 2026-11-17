package game.bot.scripts

import api.bot.Suspendable.delay
import api.bot.Suspendable.waitFor
import api.bot.action.BotCombatActionHandler
import api.bot.script.BotScript
import api.bot.script.BotScriptData
import api.bot.webwalk.plan.BankAccess
import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import com.google.gson.JsonObject
import engine.controllers.Controllers.inWilderness
import game.content.blastFurnace.BlastFurnace
import game.content.blastFurnace.BlastFurnaceInterface
import game.content.blastFurnace.BlastFurnaceWorkAction
import game.skill.smithing.BarType
import game.bot.scripts.BlastFurnaceCrew.Role
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.GroundItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.OptionDialogue
import io.luna.game.model.`object`.GameObject
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Repeats manual furnace batches using owned ore, coal, tools and fees through normal player interactions.
 * A shared crew reserves real pump, pedal, stove and repair jobs; smelters own their ore, cooling and bars.
 * Loaded ore and earned bars belong to native saved stock, so a restored visit recovers them before adding supplies.
 *
 * Reaches Keldagrim through the native entrance, boat and stairs. Banking uses the nearest reachable bank,
 * including the cache-authored basement chest, without a recovery teleport.
 * Consecutive failures, ten minutes without smelting progress and an absolute saved deadline bound the visit.
 *
 * @author lare96
 */
class BlastFurnaceBotScript(bot: Bot, val selectedBar: BarType = BarType.STEEL,
                            private var prepared: Boolean = false,
                            private val deadline: Long = System.currentTimeMillis() + 1_200_000,
                            private var failures: Int = 0,
                            val role: Role = Role.SMELTER) : BotScript(bot) {
    companion object {
        private var Bot.nextAttempt by Attr.long()

        private fun defer(bot: Bot) { bot.nextAttempt = System.currentTimeMillis() + 15 * 60_000L }

        private fun owned(bot: Bot, id: Int): Long = bot.inventory.computeAmountForId(id).toLong() +
            bot.bank.computeAmountForId(id)

        private fun canWork(bot: Bot, role: Role): Boolean =
            BotCombatActionHandler.mayGainExperience(bot, role.skillId) &&
                (role == Role.SMELTER || bot.skill(role.skillId).staticLevel >= 30 && bot.skill(role.skillId).level >= 30)

        private fun available(bot: Bot): Boolean = bot.state == EntityState.ACTIVE &&
            ActionSafety.NON_COMBAT.allows(bot) && !bot.inWilderness() &&
            System.currentTimeMillis() >= bot.nextAttempt

        private fun tools(bot: Bot, role: Role): List<Item> = when (role) {
            Role.SMELTER -> listOf(Item(if (owned(bot, BlastFurnace.WATER) > 0) BlastFurnace.WATER else BlastFurnace.BUCKET))
            Role.STOVE -> listOf(Item(if (owned(bot, BlastFurnace.COKE_SPADE) > 0) BlastFurnace.COKE_SPADE else BlastFurnace.SPADE))
            Role.REPAIR -> listOf(Item(BlastFurnace.HAMMER))
            else -> emptyList()
        }

        /**
         * Reserves missing crew jobs before travel, or admits an ore owner for a normal Smithing session.
         * A first smelter may arrive before its crew so an empty furnace can assemble without a circular gate.
         */
        fun select(bot: Bot, skillId: Int? = null, durationMillis: Long = 1_200_000): BlastFurnaceBotScript? {
            if (!available(bot)) return null
            val roles = Role.entries.filter { skillId == null || it.skillId == skillId }
                .sortedBy { if (it == Role.SMELTER) 1 else 0 }
            for (role in roles) {
                if (!canWork(bot, role) || role != Role.SMELTER && BlastFurnaceCrew.occupied(bot, role)) continue
                if (!BlastFurnace.hasAccess(bot) && owned(bot, 995) < BlastFurnace.FEE) continue
                val bar = if (role == Role.SMELTER) BarType.entries.sortedByDescending { it.level }
                    .firstOrNull { eligible(bot, it) } ?: continue else BarType.STEEL
                if (tools(bot, role).any { owned(bot, it.id) == 0L } &&
                    owned(bot, 995) <= if (BlastFurnace.hasAccess(bot)) 0 else BlastFurnace.FEE) continue
                val script = BlastFurnaceBotScript(bot, bar,
                    deadline = System.currentTimeMillis() + durationMillis, role = role)
                if (BlastFurnaceCrew.claim(bot, script, role, script.deadline)) {
                    bot.log("Blast Furnace: reserved $role.")
                    return script
                }
            }
            return null
        }

        private fun pendingWork(bot: Bot): Boolean = BlastFurnace.totalBars(bot) > 0 || BlastFurnace.beltCount(bot) > 0 ||
            BarType.entries.any { bar -> bar.level <= bot.smithing.level &&
                BlastFurnace.ingredients(bar).all { BlastFurnace.oreCount(bot, it.id) >= it.amount } }

        /**
         * Reserves tools and fee space, then fills the remaining inventory with a complete recipe.
         * Existing furnace stock contributes to the recipe; depositing ore frees room for its resulting bars.
         */
        fun supplies(bot: Bot, bar: BarType, includeOre: Boolean = true, role: Role = Role.SMELTER,
                     requireTools: Boolean = true): List<Item>? {
            if (role == Role.SMELTER && bot.smithing.staticLevel < bar.level) return null
            val supplies = tools(bot, role).toMutableList()
            if (requireTools && supplies.any { owned(bot, it.id) == 0L }) return null
            if (!BlastFurnace.hasAccess(bot)) {
                if (owned(bot, 995) < BlastFurnace.FEE) return null
                supplies += Item(995, BlastFurnace.FEE)
            }
            if (includeOre && role == Role.SMELTER) {
                val recipe = BlastFurnace.ingredients(bar)
                val slots = bot.inventory.capacity() - supplies.size
                val maximum = recipe.minOf {
                    minOf(255L / it.amount, (owned(bot, it.id) + BlastFurnace.oreCount(bot, it.id)) / it.amount).toInt()
                }
                val batches = (maximum downTo 1).firstOrNull { count ->
                    recipe.sumOf { (it.amount * count - BlastFurnace.oreCount(bot, it.id)).coerceAtLeast(0) } <= slots
                } ?: return null
                supplies += recipe.mapNotNull {
                    val amount = (it.amount * batches - BlastFurnace.oreCount(bot, it.id)).coerceAtLeast(0)
                    if (amount > 0) Item(it.id, amount) else null
                }
            }
            return supplies
        }

        fun eligible(bot: Bot): Boolean = eligible(bot, BarType.STEEL)
        fun eligible(bot: Bot, bar: BarType): Boolean = available(bot) && canWork(bot, Role.SMELTER) &&
            supplies(bot, bar, !pendingWork(bot), requireTools = false) != null
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
        override fun load(data: JsonObject) {
            bar = BarType.valueOf(data.get("bar").asString)
            prepared = data.get("prepared").asBoolean
            deadline = data.get("deadline").asLong
            failures = data.get("failures")?.asInt ?: 0
            role = data.get("role")?.asString?.let { runCatching { Role.valueOf(it) }.getOrNull() } ?: Role.SMELTER
        }
        override fun save(data: JsonObject) {
            data.addProperty("bar", bar.name)
            data.addProperty("prepared", prepared)
            data.addProperty("deadline", deadline)
            data.addProperty("failures", failures)
            data.addProperty("role", role.name)
        }
    }

    constructor(bot: Bot, data: FurnaceData) : this(bot, data.bar, data.prepared, data.deadline, data.failures, data.role)
    private var lastProgress = System.currentTimeMillis()
    private var previous: List<Any?>? = null
    override val allowsCombatResponse = false
    override fun allowsLoot(item: GroundItem): Boolean = false

    private fun valid(): Boolean = System.currentTimeMillis() < deadline && isActionSafe() &&
        BlastFurnace.present(bot) && canWork(bot, role)

    private fun plan(includeOre: Boolean = true): List<Item>? = supplies(bot, selectedBar, includeOre, role)

    private suspend fun prepareTools(): Boolean {
        val missing = tools(bot, role).filter { owned(bot, it.id) == 0L }.associate { it.id to 1 }
        if (missing.isNotEmpty()) {
            val reserve = if (BlastFurnace.hasAccess(bot)) 0 else BlastFurnace.FEE
            val budget = (owned(bot, 995) - reserve).coerceIn(0L, 1_000L).toInt()
            withTimeoutOrNull(minOf(120_000L, (deadline - System.currentTimeMillis()).coerceAtLeast(1L))) {
                handler.shop.obtainWantedItems(missing, budget, missing.keys.associateWith { 1_000L })
            }
        }
        return tools(bot, role).all { owned(bot, it.id) > 0L }
    }

    private fun pendingWork(): Boolean = pendingWork(bot)

    private fun carriedOre(): Boolean = BlastFurnace.ingredients(selectedBar).any { bot.inventory.contains(it.id) }

    override suspend fun init(resumed: Boolean): Boolean {
        if (System.currentTimeMillis() >= deadline || failures >= 5 || !available(bot) || !canWork(bot, role) ||
            !BlastFurnaceCrew.claim(bot, this, role, deadline)) return true
        BlastFurnaceCrew.refresh(bot, this)
        val finished = withTimeoutOrNull(600_000) {
            if (!prepareTools()) return@withTimeoutOrNull true
            if (!prepared || tools(bot, role).any { !bot.inventory.contains(it.id) }) {
                val plan = plan(!pendingWork()) ?: return@withTimeoutOrNull true
                if (!bank(plan)) return@withTimeoutOrNull true
                prepared = true
            }
            !returnToFurnace()
        } ?: true
        if (finished) defer(bot)
        else { resetProgress(); bot.log("Blast Furnace: arrived as $role.") }
        return finished
    }

    private fun find(position: Position, vararg ids: Int): GameObject? =
        world.locator.findObjects(position, 0) { it.id in ids }.firstOrNull()

    private suspend fun click(position: Position, vararg ids: Int): Boolean {
        val obj = find(position, *ids) ?: return false
        return handler.interactions.approach(obj, timeout = 12.seconds, allowOutOfView = true) { valid() } &&
            handler.interactions.interact(1, obj) { valid() }
    }

    /**
     * The close-window packet is also the native way to interrupt a manual operating action.
     */
    private suspend fun stopOperating() {
        output.sendCloseInterface()
        waitFor(3.seconds) { !bot.actions.contains(BlastFurnaceWorkAction::class.java) }
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
        if (!BlastFurnace.hasAccess(bot)) {
            val plan = plan(false)
            val renewed = plan != null && withTimeoutOrNull(120_000) {
                bank(plan) && returnToFurnace()
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
        if (!pendingWork() && !carriedOre()) return restock()
        val success = withTimeoutOrNull(if (bot.inventory.isFull && BlastFurnace.totalBars(bot) > 0) 120_000 else 30_000) {
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
        val plan = if (trainingComplete()) null else plan()
        if (plan == null) return finishBanking()
        val ready = withTimeoutOrNull(120_000) { bank(plan) && returnToFurnace() } == true
        if (!ready) { defer(bot); return true }
        prepared = true
        resetProgress()
        return false
    }

    private suspend fun finishBanking(): Boolean {
        BlastFurnaceCrew.release(bot, this)
        if (bot.state == EntityState.ACTIVE && isActionSafe() && !bot.inWilderness()) {
            withTimeoutOrNull(120_000) { bank(emptyList(), ending = true) }
        }
        return true
    }

    private suspend fun returnToFurnace(): Boolean {
        if (!handler.webWalkTo(BlastFurnace.ENTRY, radius = 3, attempts = 2,
                bank = BankAccess.NONE, fallback = false) || !valid()) return false
        if (BlastFurnace.hasAccess(bot)) return true
        if (bot.inventory.computeAmountForId(995) < BlastFurnace.FEE) {
            val plan = plan(false) ?: return false
            if (!bank(plan) || !handler.webWalkTo(BlastFurnace.ENTRY, radius = 3, attempts = 2,
                    bank = BankAccess.NONE, fallback = false) || !valid()) return false
        }
        val foreman = world.locator.findNpcs(BlastFurnace.FOREMAN, 6) { it.id == 2553 }.firstOrNull() ?: return false
        if (!handler.interactions.approach(foreman, timeout = 10.seconds, allowOutOfView = true) { valid() } ||
            !handler.interactions.interact(1, foreman) { valid() } ||
            !waitFor(5.seconds) { bot.overlays.has(OptionDialogue::class.java) } ||
            !handler.widgets.clickDialogueOption(1) || !waitFor(5.seconds) { BlastFurnace.hasAccess(bot) }) return false
        return handler.widgets.clickCloseInterface() && valid()
    }

    private suspend fun cycle(): Boolean {
        if (BlastFurnace.hot(bot)) {
            if (!bot.inventory.contains(BlastFurnace.WATER) &&
                (!click(BlastFurnace.SINK, 9143) || !waitFor(5.seconds) { bot.inventory.contains(BlastFurnace.WATER) })) return false
            val dispenser = find(BlastFurnace.DISPENSER, 9092, 9095) ?: return false
            return handler.inventory.useItem(BlastFurnace.WATER).onObject(dispenser) &&
                waitFor(5.seconds) { !BlastFurnace.hot(bot) }
        }
        if (BlastFurnace.totalBars(bot) > 0) {
            if (bot.inventory.isFull) {
                // Saved mixed stock can exceed one inventory. Preserve it and make as many real bank trips
                // as the saved visit budget allows; never withdraw another ore batch during claim recovery.
                val plan = plan(false) ?: return false
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
        if (carriedOre()) {
            val before = BlastFurnace.totalOre(bot)
            return click(BlastFurnace.BELT, 9100) && waitFor(5.seconds) { BlastFurnace.totalOre(bot) > before }
        }
        return maintain()
    }

    private fun handles(work: Role): Boolean = canWork(bot, work) &&
        (role == work || !BlastFurnaceCrew.occupied(bot, work, arrivedOnly = true))

    /**
     * Fills a reserved station, or assists an absent worker using only skills and tools actually held.
     * Empty belts, a hot-enough furnace and another operator are normal waiting states.
     */
    private suspend fun maintain(): Boolean {
        val damage = BlastFurnace.damage(bot)
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
        if (BlastFurnace.temperature(bot) < 75 && handles(Role.PUMP) &&
            BlastFurnace.fuel(bot) > 0 &&
            BlastFurnace.operator(bot, BlastFurnaceWorkAction.Role.PUMP).let { it == null || it === bot }) {
            val started = click(BlastFurnace.PUMP, 9090)
            val result = started && waitFor(20.seconds) {
                !valid() || BlastFurnace.temperature(bot) >= 80 || BlastFurnace.damage(bot) != null
            }
            stopOperating()
            return result
        }
        if (handles(Role.PEDALS) && BlastFurnace.hasBeltWork(bot) &&
            BlastFurnace.operator(bot, BlastFurnaceWorkAction.Role.PEDALS).let { it == null || it === bot }) {
            val started = click(BlastFurnace.PEDALS, 9097)
            val result = started && waitFor(15.seconds) {
                !valid() || !BlastFurnace.hasBeltWork(bot) || BlastFurnace.damage(bot) != null
            }
            stopOperating()
            return result
        }
        delay(1.seconds)
        return true
    }

    /**
     * Consolidates owned supplies at a native bank without the banking handler's home/recovery fallback.
     * The actual second-click bank handler must acknowledge opening before deposits and withdrawals proceed.
     */
    private suspend fun bank(plan: List<Item>, ending: Boolean = false): Boolean {
        BlastFurnaceCrew.refresh(bot, this)
        stopOperating()
        val bank = handler.webWalkToNearestBank(fallback = false) ?: return false
        if ((!ending && System.currentTimeMillis() >= deadline) || bot.inWilderness() || !isActionSafe()) return false
        if (!handler.interactions.interact(2, bank) || !waitFor(5.seconds) { bot.bank.isOpen } ||
            !handler.banking.depositInventory()) return false
        for (item in plan) if (!handler.banking.withdraw(item)) return false
        return handler.widgets.clickCloseInterface()
    }

    override suspend fun finish() {
        BlastFurnaceCrew.release(bot, this)
        output.sendCloseInterface()
        bot.actions.interruptWeak()
        bot.navigator.cancel()
        bot.walking.clear()
    }
    override fun snapshot() = FurnaceData().also {
        it.bar = selectedBar; it.prepared = prepared; it.deadline = deadline; it.failures = failures; it.role = role
    }
}
