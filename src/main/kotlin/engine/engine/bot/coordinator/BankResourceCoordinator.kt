package engine.bot.coordinator

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.action.BotBankSpace
import api.bot.action.BotCombatActionHandler
import api.bot.script.BotScript
import api.bot.zone.ProductionZones
import api.predef.*
import game.bot.scripts.skills.BuryBoneBotScript
import game.bot.scripts.skills.FiremakingBotScript
import game.bot.scripts.skills.SmeltOreBotScript
import game.skill.firemaking.Firemaking
import game.skill.firemaking.Log
import game.skill.prayer.Bone
import game.skill.smithing.BarType
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity
import kotlin.time.Duration.Companion.minutes

/**
 * Selects bounded training from expendable owned resources when the bank needs room.
 *
 * Only plans that remove more complete input stacks than their possible outputs add are selected. Existing native
 * scripts perform all production; their relief mode cannot acquire supplies, gather replacements or queue followups.
 * A persistent cooldown leaves ordinary activity selection available after unavailable facilities or failed travel.
 *
 * @author lare96
 */
object BankResourceCoordinator {
    /**
     * Upper bounds keep a relief visit short enough to exhaust a small stack instead of trimming a large one.
     */
    private const val MAX_OPERATIONS = 128
    private const val MAX_INPUTS = 256
    private const val MAX_INPUT_VALUE = 25_000.0
    private const val RETRY_MILLIS = 30 * 60_000L
    private const val EMPTY_RETRY_MILLIS = 5 * 60_000L
    private var Bot.nextResourceRelief by Attr.long().persist("bot_bank_resource_retry_at")

    /**
     * An already validated recipe and the ordinary script that can consume it.
     *
     * @author lare96
     */
    private data class Plan(val freedSlots: Int, val value: Double, val operations: Int, val create: () -> BotScript)

    /**
     * Chooses an owned-resource session before elective bank cleaning, charging its cooldown before dispatch.
     */
    @JvmStatic
    @JvmOverloads
    fun select(bot: Bot, now: Long = System.currentTimeMillis()): BotScript? {
        if (bot.bank.computeRemainingSize() > BotBankSpace.START_FREE_SLOTS || now < bot.nextResourceRelief ||
            bot.preferences.isMerchant || !BotScript.ActionSafety.NON_COMBAT.allows(bot)) return null
        val plans = ArrayList<Plan>()
        for (log in Log.entries) {
            if (bot.firemaking.staticLevel < log.level) continue
            plan(bot, SKILL_FIREMAKING, listOf(Item(log.id)), emptySet(), setOf(Firemaking.TINDERBOX)) {
                FiremakingBotScript(bot, log, 10.minutes,
                    FiremakingBotScript.FiremakingSpot.entries.map { it.zone }.toMutableList(), bankRelief = true)
            }?.let(plans::add)
        }
        for (bone in Bone.entries) {
            plan(bot, SKILL_PRAYER, listOf(bone.boneItem), emptySet()) {
                BuryBoneBotScript(bot, bone, 10.minutes, bankRelief = true)
            }?.let(plans::add)
        }
        for (bar in BarType.entries) {
            if (bot.smithing.staticLevel < bar.level) continue
            plan(bot, SKILL_SMITHING, bar.oreList, setOf(bar.id)) {
                SmeltOreBotScript(bot, bar, 10.minutes, ProductionZones.FURNACES.toMutableList(), bankRelief = true)
            }?.let(plans::add)
        }
        val selected = plans.minWithOrNull(compareBy<Plan> { it.value / it.freedSlots }
            .thenBy { it.operations })
        bot.nextResourceRelief = now + if (selected == null) EMPTY_RETRY_MILLIS else RETRY_MILLIS
        return selected?.create?.invoke()
    }

    /**
     * Rechecks live reserves and physically held inputs before relief startup, withdrawal and production.
     * The active relief script protects its tools without reserving its own expendable inputs.
     */
    fun canProcess(bot: Bot, skill: Int, materials: List<Item>, tools: Set<Int> = emptySet()): Boolean {
        if (materials.isEmpty() || materials.any { it.amount <= 0 || it.id in tools } ||
            materials.map { it.id }.distinct().size != materials.size) return false
        if (bot.preferences.isMerchant || !bot.preferences.canChooseActivity(BotActivity.TRAINING_SKILLS) ||
            skill !in bot.preferences.skills || !BotCombatActionHandler.wantsToTrain(bot, skill)) return false
        val retained = BotBankSpace.retainedStock(bot)
        val contents = bot.bank.filterNotNull() + bot.inventory.filterNotNull()
        if (materials.any { input ->
                (retained[input.id] ?: 0) > 0 || contents.any {
                    it.itemDef.unnotedId.orElse(it.id) == input.id &&
                        (it.isDynamic || it.itemDef.isNoted || !it.itemDef.isTradeable)
                } || stock(bot, input.id) < input.amount
            }) return false
        if (tools.any { tool -> stock(bot, tool) < 1 || contents.any { it.id == tool && it.isDynamic } }) return false
        val operations = materials.minOf { stock(bot, it.id) / it.amount }
        if (operations !in 1..MAX_OPERATIONS.toLong() ||
            operations * materials.sumOf { it.amount.toLong() } > MAX_INPUTS) return false
        var value = 0.0
        for (input in materials) {
            val price = world.economy.getRealPrice(input.id)
            if (!price.isFinite() || price <= 0.0) return false
            value += price * input.amount * operations
        }
        return value <= MAX_INPUT_VALUE
    }

    /**
     * Evaluates complete input exhaustion and every possible product stack, without treating withdrawals as disposal.
     */
    private fun plan(bot: Bot, skill: Int, materials: List<Item>, outputs: Set<Int>, tools: Set<Int> = emptySet(),
                     create: () -> BotScript): Plan? {
        if (!canProcess(bot, skill, materials, tools)) return null
        val owned = materials.associate { it.id to stock(bot, it.id) }
        val operations = materials.minOf { owned.getValue(it.id) / it.amount }.toInt()
        val bankIds = bot.bank.filterNotNull().filterNot { it.isDynamic }.map { it.id }.toSet()
        if (outputs.any { id -> bot.bank.any { it != null && it.id == id && it.isDynamic } }) return null
        if (outputs.any { stock(bot, it) + operations > Int.MAX_VALUE }) return null
        val slots = projectedSlots(owned, bankIds, materials.associate { it.id to it.amount }, outputs, operations)
        if (slots < 1) return null
        val firstBatch = minOf(operations, bot.inventory.capacity() / materials.sumOf { it.amount })
        val firstRemoved = materials.count { it.id in bankIds && owned.getValue(it.id) == it.amount.toLong() * firstBatch }
        if (outputs.count { it !in bankIds } > bot.bank.computeRemainingSize() + firstRemoved) return null
        var value = 0.0
        for (input in materials) {
            val price = world.economy.getRealPrice(input.id)
            if (!price.isFinite() || price <= 0.0) return null
            value += price * input.amount * operations
        }
        return if (value <= MAX_INPUT_VALUE) Plan(slots, value, operations, create) else null
    }

    /**
     * Computes final bank-slot savings for a finite recipe, counting a new product as one occupied slot.
     */
    internal fun projectedSlots(owned: Map<Int, Long>, bankIds: Set<Int>, inputs: Map<Int, Int>,
                                outputs: Set<Int>, operations: Int): Int {
        if (operations < 1 || inputs.isEmpty()) return 0
        val removed = inputs.count { (id, amount) ->
            id in bankIds && amount > 0 && owned.getOrDefault(id, 0) == amount.toLong() * operations
        }
        return removed - outputs.count { it !in bankIds }
    }

    /**
     * Counts ordinary unnoted stock without treating equipment, notes or dynamic instances as processable inputs.
     */
    private fun stock(bot: Bot, id: Int): Long =
        bot.bank.computeNonDynamicAmountForId(id).toLong() + bot.inventory.computeNonDynamicAmountForId(id)
}
