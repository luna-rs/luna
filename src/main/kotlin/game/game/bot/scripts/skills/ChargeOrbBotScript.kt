package game.bot.scripts.skills

import api.bot.script.InventoryBotScript
import api.bot.script.InventoryBotScript.Companion.InventoryScriptData
import api.bot.script.ownsProductionSupplies
import api.bot.script.productionRuneCosts
import api.bot.script.bypassesSpellCosts
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import com.google.gson.JsonObject
import game.skill.magic.Magic
import game.skill.magic.chargeOrb.ChargeOrbAction
import game.skill.magic.chargeOrb.ChargeOrbAction.Companion.UNPOWERED_ORB
import game.skill.magic.chargeOrb.ChargeOrbAction.Companion.chargeOrbDelay
import game.skill.magic.chargeOrb.ChargeOrbType
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Spellbook
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration

/**
 * Charges owned unpowered orbs at the matching elemental obelisk through the normal player packet.
 *
 * All four recipes use [ChargeOrbType] for levels, spell widgets, obelisk IDs, products, and rune costs.
 * The script requires the regular spellbook and permanent recipe level for selection, and rechecks the
 * current level, spellbook, carried costs, and safety before casting. Equipped elemental staffs and a
 * single sufficient combination-rune stack can substitute the base elemental runes. It does not equip
 * or acquire a staff automatically. Shared Magic validation resolves the actual cost at each cast.
 *
 * Rune stacks reserve one inventory slot each; remaining slots hold orbs, limited by banked spell costs.
 * Beta/administrator cost bypasses retain normal gameplay semantics: batches reserve half the inventory
 * for free outputs, and output-space checks trigger banking before another cast. Inputs are still required
 * for activity selection. [InventoryBotScript] supplies verified unnoted withdrawals, travel fallback,
 * bounded interaction/banking retries, progress detection, session expiry, and persistent counters.
 *
 * The script resolves a loaded matching obelisk inside the selected subzone, then queues the shared
 * magic-on-object packet. [ChargeOrbAction] owns delayed conversion, cost checks, effects, cooldown, and XP.
 * Production is confirmed by input consumption or newly stored charged orbs. Snapshots contain the spell,
 * duration, zones, and retry counters; live objects and rune plans are resolved again after restoration.
 *
 * @param bot The bot charging orbs.
 * @property type Existing elemental charge-orb spell.
 * @param duration Session duration managed by the inherited lifecycle.
 * @param zones Candidate matching-obelisk zones supplied by the factory.
 * @author lare96
 */
class ChargeOrbBotScript(bot: Bot, val type: ChargeOrbType, duration: Duration, zones: MutableList<SubZone>) :
    InventoryBotScript(bot, duration, zones) {
    override val verifiedProductionWithdrawals = true

    companion object {
        /**
         * Spell selection stored alongside the inherited session configuration and failure budgets.
         *
         * @author lare96
         */
        class ChargeOrbData : InventoryScriptData() {
            /** [ChargeOrbType] enum name used to reconstruct the spell. */
            var recipe = ""
            override fun load(data: JsonObject) {
                super.load(data)
                recipe = data.get("recipe")?.asString ?: ""
            }
            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("recipe", recipe)
            }
        }
    }

    /** Permanent Magic level needed for selection and startup. */
    val requiredLevel = type.level

    /** Restores the spell, session configuration, and inherited retry counters. */
    constructor(bot: Bot, data: ChargeOrbData) : this(bot, ChargeOrbType.valueOf(data.recipe), data.duration, data.zones) {
        restoreInventoryState(data)
    }

    /** Minimum owned inputs required by the normal production startup and wanted-item handling. */
    private fun materials() = listOf(Item(UNPOWERED_ORB)) + bot.productionRuneCosts(type.requirements)

    /** Checks permanent level, regular spellbook, and owned inputs across bank/inventory. */
    fun isEligible(): Boolean = bot.spellbook == Spellbook.REGULAR &&
        bot.skill(SKILL_MAGIC).staticLevel >= requiredLevel && bot.ownsProductionSupplies(materials())

    /** Returns a balanced orb/rune batch, reserving one slot per stack and limiting casts to banked costs. */
    fun bankBatch(): List<Item> {
        val costs = bot.productionRuneCosts(type.requirements, bankedOnly = true)
        val capacity = if (bot.bypassesSpellCosts()) bot.inventory.capacity() / 2 else bot.inventory.capacity() - costs.size
        val amount = minOf(capacity, bot.bank.computeAmountForId(UNPOWERED_ORB),
            costs.minOfOrNull { bot.bank.computeAmountForId(it.id) / it.amount } ?: Int.MAX_VALUE)
        if (amount < 1) return emptyList()
        return listOf(Item(UNPOWERED_ORB, amount)) + costs.map { Item(it.id, it.amount * amount) }
    }

    override fun withdraw(): List<Item> = productionWithdraw(materials(), levelEligible =
        bot.spellbook == Spellbook.REGULAR && bot.skill(SKILL_MAGIC).staticLevel >= requiredLevel)

    override fun bankWithdraw(): List<Item> = bankBatch().also { if (it.isEmpty()) stop() }

    /** Checks carried inputs/costs, including output space for spells whose costs are bypassed. */
    private fun canContinue(): Boolean = bot.inventory.contains(UNPOWERED_ORB) &&
        (!bot.bypassesSpellCosts() || bot.inventory.hasSpaceFor(Item(type.chargedOrb))) &&
        Magic.checkRequirements(bot, type.level, type.requirements) != null

    override suspend fun onInventoryBankRequested(): Boolean = requestProductionBank(canContinue())

    override suspend fun onExecuteInZone(): Boolean {
        if (!productionReady(canContinue(), levelEligible = bot.spellbook == Spellbook.REGULAR &&
                bot.skill(SKILL_MAGIC).level >= requiredLevel)) return true
        attemptProduction(UNPOWERED_ORB, outputId = type.chargedOrb) {
            val zone = activeZone ?: return@attemptProduction false
            val obelisk = world.locator.findObjects(zone.area.centerPosition, zone.area.tileRadius) {
                it.position in zone.area && it.id == type.objectId
            }.firstOrNull() ?: return@attemptProduction false
            output.useSpellOnObject(type.spellId, obelisk)
        }
        return true
    }

    override suspend fun finish() {
        bot.chargeOrbDelay.cancelQueuedTask()
    }

    override fun snapshot(): ChargeOrbData = ChargeOrbData().also {
        it.recipe = type.name
        saveInventoryState(it)
    }
}
