package game.bot.scripts.skills

import api.bot.Suspendable.waitFor
import api.bot.script.InventoryBotScript
import api.bot.script.InventoryBotScript.Companion.InventoryScriptData
import api.bot.script.ownsProductionSupplies
import api.bot.script.productionRuneCosts
import api.bot.zone.SubZone
import api.predef.*
import com.google.gson.JsonObject
import engine.bot.gear.BotGearLocator
import engine.controllers.Controllers.inWilderness
import game.skill.magic.Magic
import game.skill.magic.Rune
import game.skill.magic.SpellCost
import game.skill.magic.teleportSpells.TeleportAction.Companion.teleportDelay
import game.skill.magic.teleportSpells.TeleportSpell
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Spellbook
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Trains Magic by repeatedly casting an owned, regular-spellbook city teleport through its native button.
 *
 * Varrock, Lumbridge, Falador and Camelot use existing safe town work zones. Each native landing remains within
 * the shared work-zone distance, so repeated casts do not start a walk back to the bank. Quest-dependent spells
 * and hazardous destinations are excluded. The player action owns teleport restrictions, rune payment, XP and
 * animation; consuming law runes proves progress even when successive casts land on the same tile.
 *
 * The inventory-script lifecycle supplies banking, verified withdrawals, account-build XP guards and bounded
 * failures. Each trip carries at most 100 casts and retains the equipped staff, allowing the shared rune planner
 * to use its elemental substitutions. No supplies are generated. Native teleport completion and cooldown are
 * awaited before another click; pausing or ending the script does not cancel a paid teleport in flight.
 *
 * @param bot The bot training Magic.
 * @property spell The supported city teleport used for this session.
 * @param duration Remaining session time.
 * @author lare96
 */
class TeleportTrainingBotScript(bot: Bot, val spell: TeleportSpell, duration: Duration) :
    InventoryBotScript(bot, duration, mutableListOf(SITES.getValue(spell))) {
    override val trainingSkill = SKILL_MAGIC
    override val verifiedProductionWithdrawals = true

    companion object {
        /**
         * Existing safe banking/work areas close enough to retain their native teleport landing.
         * Explicit membership also excludes quest unlocks, consumable non-rune costs and Wilderness arrivals.
         */
        val SITES = mapOf(
            TeleportSpell.VARROCK to SubZone.VARROCK_SQUARE,
            TeleportSpell.LUMBRIDGE to SubZone.LUMBRIDGE_COURT_YARD,
            TeleportSpell.FALADOR to SubZone.FALADOR_WEST_BANK,
            TeleportSpell.CAMELOT to SubZone.SEERS_VILLAGE_MAIN
        )

        /**
         * Limits carried rune exposure while amortizing the normal banking trip across multiple casts.
         */
        const val CASTS_PER_TRIP = 100

        /**
         * Saves the selected native spell alongside shared duration and retry state.
         *
         * @author lare96
         */
        class TeleportTrainingData : InventoryScriptData() {
            /**
             * Native enum name; older data without a selection starts with the level-25 city spell.
             */
            var spell = TeleportSpell.VARROCK.name

            override fun load(data: JsonObject) {
                super.load(data)
                spell = data.get("spell")?.asString ?: TeleportSpell.VARROCK.name
            }

            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("spell", spell)
            }
        }
    }

    /**
     * Restores only a supported spell and the shared lifecycle state; work zones follow the spell's native landing.
     */
    constructor(bot: Bot, data: TeleportTrainingData) : this(bot, TeleportSpell.valueOf(data.spell), data.duration) {
        restoreInventoryState(data)
    }

    /**
     * Checks the native spellbook and permanent level before considering owned stock for one cast.
     */
    fun isEligible(): Boolean = bot.spellbook == Spellbook.REGULAR && bot.magic.staticLevel >= spell.level &&
        bot.ownsProductionSupplies(bot.productionRuneCosts(spell.requirements))

    /**
     * Balances rune stacks after the shared deposit, accepting partial final batches and staff/combination runes.
     */
    fun bankBatch(): List<Item> {
        val costs = bot.productionRuneCosts(spell.requirements, bankedOnly = true)
        val casts = minOf(CASTS_PER_TRIP, costs.minOf { bot.bank.computeAmountForId(it.id) / it.amount })
        return if (casts < 1) emptyList() else costs.map { Item(it.id, it.amount * casts) }
    }

    override suspend fun equipment(): BotGearLocator? = null

    override suspend fun beforeInventoryInit(): Boolean = !bot.inWilderness() || handler.combat.fleeWilderness()

    override fun withdraw(): List<Item> = productionWithdraw(bot.productionRuneCosts(spell.requirements),
        levelEligible = bot.spellbook == Spellbook.REGULAR && bot.magic.staticLevel >= spell.level)

    override fun bankWithdraw(): List<Item> = bankBatch().also { if (it.isEmpty()) stop() }

    /**
     * Quotes carried player spell costs without repeatedly sending missing-rune messages.
     */
    private fun canContinue(): Boolean = Magic.spellCost(bot, spell.level, spell.requirements) is SpellCost.Castable

    override suspend fun onInventoryBankRequested(): Boolean = requestProductionBank(canContinue())

    override suspend fun onExecuteInZone(): Boolean {
        if (!productionReady(canContinue(), levelEligible = bot.spellbook == Spellbook.REGULAR &&
                bot.magic.level >= spell.level && !bot.status.isTeleBlocked() && !bot.inWilderness())) return true
        if (!waitFor(5.seconds) { bot.teleportDelay.ready(2) || !isActionSafe() } || !isActionSafe()) return true
        attemptProduction(Rune.LAW.id) { output.clickButton(spell.button) }
        if (!waitFor(10.seconds) { !bot.isLocked }) { stop(); return true }
        return true
    }

    override fun snapshot(): TeleportTrainingData = TeleportTrainingData().also {
        it.spell = spell.name
        saveInventoryState(it)
    }
}
