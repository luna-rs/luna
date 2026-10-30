package game.bot.scripts

import api.bot.Suspendable.delay
import api.bot.Suspendable.waitFor
import api.bot.action.BotCombatActionHandler
import api.bot.script.BotScript
import api.bot.script.BotScriptData
import api.bot.webwalk.plan.BankAccess
import api.predef.*
import com.google.gson.JsonObject
import engine.controllers.Controllers.inWilderness
import game.content.blastFurnace.BlastFurnace
import game.content.blastFurnace.BlastFurnaceInterface
import game.content.blastFurnace.BlastFurnaceWorkAction
import game.skill.smithing.BarType
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
 * Runs one small manual furnace batch using owned ore, coal, tools and fees through normal player interactions.
 * The bot alternates real coke, pump, pedal, repair, cooling and dispenser actions; no worker NPC is simulated.
 * Loaded ore and earned bars belong to native saved stock, so a restored visit recovers them before adding supplies.
 *
 * Admission is local to Keldagrim and the basement until a complete boat route is supported. Banking uses the
 * ordinary upstairs bank and native stairs, never a modern furnace bank chest or a recovery teleport.
 * Consecutive failures, two minutes without stock/machine progress and an absolute saved deadline bound the visit.
 *
 * @author lare96
 */
class BlastFurnaceBotScript(bot: Bot, val selectedBar: BarType = BarType.STEEL,
                            private var prepared: Boolean = false,
                            private val deadline: Long = System.currentTimeMillis() + 480_000,
                            private var failures: Int = 0) : BotScript(bot) {
    companion object {
        /**
         * Only locations with an existing native connection to the real Keldagrim bank are selected automatically.
         */
        fun local(position: Position): Boolean = BlastFurnace.inside(position) ||
            (position.z == 0 && position.x in 2816..2943 && position.y in 10112..10239)

        private fun owned(bot: Bot, id: Int): Long = bot.inventory.computeAmountForId(id).toLong() +
            bot.bank.computeAmountForId(id)

        private fun operatorsReady(bot: Bot): Boolean = listOf(SKILL_STRENGTH, SKILL_AGILITY, SKILL_CRAFTING, SKILL_FIREMAKING)
            .all { bot.skill(it).staticLevel >= 30 && BotCombatActionHandler.mayGainExperience(bot, it) }

        /**
         * Reserves tools and fee space before calculating an indivisible batch of existing BarType ingredients.
         * At most six bars keeps solo pump/pedal/cooling work short and leaves room for a return trip's bars.
         */
        fun supplies(bot: Bot, bar: BarType, includeOre: Boolean = true): List<Item>? {
            if (bot.smithing.staticLevel < bar.level) return null
            val spade = when {
                owned(bot, BlastFurnace.SPADE) > 0 -> BlastFurnace.SPADE
                owned(bot, BlastFurnace.COKE_SPADE) > 0 -> BlastFurnace.COKE_SPADE
                else -> return null
            }
            val bucket = when {
                owned(bot, BlastFurnace.BUCKET) > 0 -> BlastFurnace.BUCKET
                owned(bot, BlastFurnace.WATER) > 0 -> BlastFurnace.WATER
                else -> return null
            }
            if (owned(bot, BlastFurnace.HAMMER) == 0L) return null
            val supplies = mutableListOf(Item(spade), Item(bucket), Item(BlastFurnace.HAMMER))
            if (!BlastFurnace.hasAccess(bot)) {
                if (owned(bot, 995) < BlastFurnace.FEE) return null
                supplies += Item(995, BlastFurnace.FEE)
            }
            if (includeOre) {
                val recipe = BlastFurnace.ingredients(bar)
                val slots = 28 - supplies.size - 2
                val batches = minOf(6, slots / recipe.sumOf { it.amount },
                    recipe.minOf { (owned(bot, it.id) / it.amount).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() })
                if (batches <= 0) return null
                supplies += recipe.map { Item(it.id, it.amount * batches) }
            }
            return supplies
        }

        fun eligible(bot: Bot): Boolean = eligible(bot, BarType.STEEL)
        fun eligible(bot: Bot, bar: BarType): Boolean = bot.state == EntityState.ACTIVE &&
            ActionSafety.NON_COMBAT.allows(bot) && !bot.inWilderness() && local(bot.position) &&
            operatorsReady(bot) &&
            supplies(bot, bar, BlastFurnace.totalOre(bot) + BlastFurnace.totalBars(bot) == 0) != null
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
        override fun load(data: JsonObject) {
            bar = BarType.valueOf(data.get("bar").asString)
            prepared = data.get("prepared").asBoolean
            deadline = data.get("deadline").asLong
            failures = data.get("failures")?.asInt ?: 0
        }
        override fun save(data: JsonObject) {
            data.addProperty("bar", bar.name)
            data.addProperty("prepared", prepared)
            data.addProperty("deadline", deadline)
            data.addProperty("failures", failures)
        }
    }

    constructor(bot: Bot, data: FurnaceData) : this(bot, data.bar, data.prepared, data.deadline, data.failures)
    private var lastProgress = System.currentTimeMillis()
    private var previous: List<Any?>? = null
    override val allowsCombatResponse = false
    override fun allowsLoot(item: GroundItem): Boolean = false

    private fun valid(): Boolean = System.currentTimeMillis() < deadline && isActionSafe() &&
        BlastFurnace.inside(bot.position) && operatorsReady(bot)

    override suspend fun init(resumed: Boolean): Boolean {
        if (System.currentTimeMillis() >= deadline || failures >= 5 || !eligible(bot, selectedBar)) return true
        return withTimeoutOrNull(180_000) {
            val pending = BlastFurnace.totalOre(bot) + BlastFurnace.totalBars(bot) > 0
            if (!prepared || !bot.inventory.contains(BlastFurnace.HAMMER) ||
                !bot.inventory.containsAny(BlastFurnace.SPADE, BlastFurnace.COKE_SPADE) ||
                !bot.inventory.containsAny(BlastFurnace.BUCKET, BlastFurnace.WATER)) {
                val plan = supplies(bot, selectedBar, !pending) ?: return@withTimeoutOrNull true
                if (!bank(plan)) return@withTimeoutOrNull true
                prepared = true
            }
            if (!handler.webWalkTo(BlastFurnace.ENTRY, radius = 3, bank = BankAccess.NONE, fallback = false) || !valid())
                return@withTimeoutOrNull true
            if (!BlastFurnace.hasAccess(bot)) {
                val foreman = world.locator.findNpcs(BlastFurnace.FOREMAN, 6) { it.id == 2553 }.firstOrNull()
                    ?: return@withTimeoutOrNull true
                if (!handler.interactions.approach(foreman, timeout = 10.seconds, allowOutOfView = true) { valid() } ||
                    !handler.interactions.interact(1, foreman) { valid() } ||
                    !waitFor(5.seconds) { bot.overlays.has(OptionDialogue::class.java) } ||
                    !handler.widgets.clickDialogueOption(1) || !waitFor(5.seconds) { BlastFurnace.hasAccess(bot) })
                    return@withTimeoutOrNull true
                handler.widgets.clickCloseInterface()
            }
            false
        } ?: true
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
            BlastFurnace.temperature(bot), BlastFurnace.fuel(bot), BlastFurnace.damage(bot), BlastFurnace.hot(bot))
        if (state != previous) { previous = state; lastProgress = System.currentTimeMillis() }
    }

    override suspend fun run(): Boolean {
        if (!valid() || !BlastFurnace.hasAccess(bot) || failures >= 5) return true
        observe()
        if (System.currentTimeMillis() - lastProgress >= 120_000) return true
        val success = withTimeoutOrNull(if (bot.inventory.isFull && BlastFurnace.totalBars(bot) > 0) 120_000 else 30_000) {
            cycle()
        } == true
        failures = if (success) 0 else failures + 1
        delay(1.seconds)
        return BlastFurnace.totalOre(bot) + BlastFurnace.totalBars(bot) == 0 &&
            BlastFurnace.ingredients(selectedBar).none { bot.inventory.contains(it.id) }
    }

    private suspend fun cycle(): Boolean {
        val damage = BlastFurnace.damage(bot)
        if (damage != null) return click(damage.position, damage.broken) &&
            waitFor(5.seconds) { BlastFurnace.damage(bot) != damage }
        if (BlastFurnace.hot(bot)) {
            if (!bot.inventory.contains(BlastFurnace.WATER) &&
                (!click(BlastFurnace.SINK, 9143) || !waitFor(5.seconds) { bot.inventory.contains(BlastFurnace.WATER) })) return false
            val dispenser = find(BlastFurnace.DISPENSER, 9092, 9095) ?: return false
            return handler.inventory.useItem(BlastFurnace.WATER).onObject(dispenser) &&
                waitFor(5.seconds) { !BlastFurnace.hot(bot) }
        }
        if (BlastFurnace.totalBars(bot) > 0) {
            if (bot.inventory.isFull) {
                // Saved mixed stock can exceed a fresh six-bar batch. Preserve it and make as many real bank trips
                // as the saved visit budget allows; never withdraw another ore batch during claim recovery.
                val plan = supplies(bot, selectedBar, false) ?: return false
                return bank(plan) && handler.webWalkTo(BlastFurnace.ENTRY, radius = 3,
                    bank = BankAccess.NONE, fallback = false) && valid()
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
        if (BlastFurnace.ingredients(selectedBar).any { bot.inventory.contains(it.id) }) {
            val before = BlastFurnace.totalOre(bot)
            return click(BlastFurnace.BELT, 9100) && waitFor(5.seconds) { BlastFurnace.totalOre(bot) > before }
        }
        if (BlastFurnace.totalOre(bot) == 0) return true
        if (BlastFurnace.fuel(bot) < 20) {
            if (!bot.inventory.contains(BlastFurnace.COKE_SPADE) &&
                (!click(BlastFurnace.COKE, 9088) || !waitFor(5.seconds) { bot.inventory.contains(BlastFurnace.COKE_SPADE) }))
                return false
            val before = BlastFurnace.fuel(bot)
            return click(BlastFurnace.STOVE, 9085, 9086, 9087) && waitFor(5.seconds) { BlastFurnace.fuel(bot) > before }
        }
        if (BlastFurnace.temperature(bot) < 75) {
            val started = click(BlastFurnace.PUMP, 9090)
            val result = started && waitFor(20.seconds) {
                !valid() || BlastFurnace.temperature(bot) >= 80 || BlastFurnace.damage(bot) != null
            }
            stopOperating()
            return result
        }
        if (BlastFurnace.temperature(bot) > 90) { delay(3.seconds); return true }
        if (BlastFurnace.beltCount(bot) > 0) {
            val started = click(BlastFurnace.PEDALS, 9097)
            val result = started && waitFor(15.seconds) {
                !valid() || BlastFurnace.beltCount(bot) == 0 || BlastFurnace.damage(bot) != null
            }
            stopOperating()
            return result
        }
        delay(2.seconds)
        return true
    }

    /**
     * Consolidates owned supplies at a native bank without the banking handler's home/recovery fallback.
     * The actual second-click bank handler must acknowledge opening before deposits and withdrawals proceed.
     */
    private suspend fun bank(plan: List<Item>): Boolean {
        stopOperating()
        val bank = handler.webWalkToNearestBank(fallback = false) ?: return false
        if (System.currentTimeMillis() >= deadline || !local(bot.position) || !isActionSafe()) return false
        if (!handler.interactions.interact(2, bank) || !waitFor(5.seconds) { bot.bank.isOpen } ||
            !handler.banking.depositInventory()) return false
        for (item in plan) if (!handler.banking.withdraw(item)) return false
        return handler.widgets.clickCloseInterface()
    }

    override suspend fun finish() { output.sendCloseInterface(); bot.navigator.cancel(); bot.walking.clear() }
    override fun snapshot() = FurnaceData().also {
        it.bar = selectedBar; it.prepared = prepared; it.deadline = deadline; it.failures = failures
    }
}
