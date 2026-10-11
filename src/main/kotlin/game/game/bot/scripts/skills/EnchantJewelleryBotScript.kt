package game.bot.scripts.skills

import api.bot.script.InventoryBotScript
import api.bot.script.InventoryBotScript.Companion.InventoryScriptData
import api.bot.script.StationaryInventoryBotScript
import api.bot.script.ownsProductionSupplies
import api.bot.script.productionRuneCosts
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import com.google.gson.JsonObject
import game.skill.magic.Magic
import game.skill.magic.enchantJewellery.EnchantJewelleryAction.Companion.enchantJewelleryDelay
import game.skill.magic.enchantJewellery.EnchantJewelleryType
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Spellbook
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration

/**
 * Enchants owned jewellery through the existing magic-on-inventory-item interaction.
 *
 * All fourteen inputs in the six existing enchantment maps share this script; no additional recipes or
 * item IDs are invented. The regular spellbook and permanent Magic level determine selection. Current
 * level, carried rune costs, and safety are checked before casting. The ordinary player action owns item
 * replacement, rune consumption, experience, effects, and its three-tick queued cooldown.
 *
 * Shared rune planning supports equipped staffs and sufficient combination stacks, including one stack
 * covering both elements of dragonstone/onyx spells. Each rune stack reserves one slot; remaining slots
 * hold unnoted jewellery, limited by banked costs. Enchantment replaces its input in place, so free casts
 * can use all twenty-eight slots. Staff acquisition and fragmented partial rune planning are not included.
 *
 * [InventoryBotScript] supplies banking, verified withdrawals, bounded retries, safety, and progress
 * detection. Missing consumables request total stock targets of 1,000. Snapshots preserve the spell,
 * input, duration, zones, and retry counters; rune plans and live slots are resolved again after restoration.
 *
 * @param bot The bot enchanting jewellery.
 * @property type Existing enchantment spell selected for the session.
 * @property inputId Supported unenchanted jewellery item processed by that spell.
 * @param duration Session duration managed by the inherited lifecycle.
 * @param zones Safe processing hubs with existing banking/travel support.
 * @author lare96
 */
class EnchantJewelleryBotScript(
    bot: Bot, val type: EnchantJewelleryType, val inputId: Int, duration: Duration,
    zones: MutableList<SubZone> = StationaryInventoryBotScript.DEFAULT_ZONES.toMutableList()
) : InventoryBotScript(bot, duration, zones) {
    override val verifiedProductionWithdrawals = true

    companion object {
        /** Spell/input pairs accepted by the existing player enchanting plugin. */
        val RECIPES = EnchantJewelleryType.entries.flatMap { type -> type.enchantMap.keys.map { type to it } }

        /**
         * Existing spell and jewellery selection stored alongside inherited session and retry data.
         *
         * @author lare96
         */
        class EnchantJewelleryData : InventoryScriptData() {
            /** Existing enchantment enum name used to restore the spell. */
            var spell = ""
            /** Unenchanted item identifier validated against the restored spell's map. */
            var inputId = -1
            override fun load(data: JsonObject) {
                super.load(data)
                spell = data.get("spell")?.asString ?: ""
                inputId = data.get("inputId")?.asInt ?: -1
            }
            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("spell", spell)
                data.addProperty("inputId", inputId)
            }
        }
    }

    init { require(inputId in type.enchantMap) }

    /** Permanent Magic level required by the existing spell. */
    val requiredLevel = type.level
    /** Product identifier supplied by the existing enchantment map. */
    val outputId = type.enchantMap.getValue(inputId).id

    /** Restores the spell, validated input, session configuration, and inherited retry budgets. */
    constructor(bot: Bot, data: EnchantJewelleryData) : this(bot, EnchantJewelleryType.valueOf(data.spell),
        data.inputId, data.duration, data.zones) { restoreInventoryState(data) }

    /** Minimum owned input and one cast's rune costs used by startup and wanted-item handling. */
    private fun materials() = listOf(Item(inputId)) + bot.productionRuneCosts(type.requirements)

    /** Checks regular spellbook, permanent level, and supplies across inventory and bank. */
    fun isEligible(): Boolean = bot.spellbook == Spellbook.REGULAR &&
        bot.skill(SKILL_MAGIC).staticLevel >= requiredLevel && bot.ownsProductionSupplies(materials())

    /** Balances banked jewellery with rune costs, reserving one inventory slot per distinct stack. */
    fun bankBatch(): List<Item> {
        val costs = bot.productionRuneCosts(type.requirements, bankedOnly = true)
        val amount = minOf(bot.inventory.capacity() - costs.size, bot.bank.computeAmountForId(inputId),
            costs.minOfOrNull { bot.bank.computeAmountForId(it.id) / it.amount } ?: Int.MAX_VALUE)
        if (amount < 1) return emptyList()
        return listOf(Item(inputId, amount)) + costs.map { Item(it.id, it.amount * amount) }
    }

    override fun withdraw(): List<Item> = productionWithdraw(materials(), levelEligible =
        bot.spellbook == Spellbook.REGULAR && bot.skill(SKILL_MAGIC).staticLevel >= requiredLevel)

    override fun bankWithdraw(): List<Item> = bankBatch().also { if (it.isEmpty()) stop() }

    /** Whether the carried jewellery and the normal Magic validator allow another cast. */
    private fun canContinue(): Boolean = bot.inventory.contains(inputId) &&
        Magic.checkRequirements(bot, type.level, type.requirements) != null

    override suspend fun onInventoryBankRequested(): Boolean = requestProductionBank(canContinue())

    override suspend fun onExecuteInZone(): Boolean {
        if (!productionReady(canContinue(), levelEligible = bot.spellbook == Spellbook.REGULAR &&
                bot.skill(SKILL_MAGIC).level >= requiredLevel)) return true
        attemptProduction(inputId, outputId = outputId) { handler.inventory.useSpellOnItem(type.spellId, inputId) }
        return true
    }

    override suspend fun finish() { bot.enchantJewelleryDelay.cancelQueuedTask() }

    override fun snapshot(): EnchantJewelleryData = EnchantJewelleryData().also {
        it.spell = type.name
        it.inputId = inputId
        saveInventoryState(it)
    }
}
