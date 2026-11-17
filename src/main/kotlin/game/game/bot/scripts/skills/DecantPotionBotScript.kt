package game.bot.scripts.skills

import api.bot.action.BotPotionDecanting
import api.bot.action.trading.BotTradeIntentions
import api.bot.script.InventoryBotScript
import api.bot.script.InventoryBotScript.Companion.InventoryScriptData
import api.bot.script.ProductionRecipe
import api.bot.script.StationaryInventoryBotScript
import api.bot.script.productionBatch
import api.bot.zone.SubZone
import api.predef.SKILL_HERBLORE
import com.google.gson.JsonObject
import engine.bot.coordinator.skill.BotProductionPlanner
import game.player.item.consume.potion.PotionFamily
import io.luna.game.model.EntityState
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration

/**
 * Converts a finite, funded potion basket with ordinary manual combining, one complete four-bottle round at a time.
 * Interrupted rounds end their goal with every real remainder retained, rather than replenishing spent inputs.
 *
 * @author lare96
 */
class DecantPotionBotScript(
    bot: Bot,
    val family: PotionFamily,
    duration: Duration,
    zones: MutableList<SubZone> = StationaryInventoryBotScript.zonesForSkill(SKILL_HERBLORE)
) : InventoryBotScript(bot, duration, zones) {
    companion object {
        const val EMPTY_VIAL = 229

        /**
         * The economic round contains every bottle, including its explicitly zero-valued empty vial.
         */
        fun recipe(family: PotionFamily) = ProductionRecipe("decant:${family.fourDose}", SKILL_HERBLORE, 1,
            mapOf(family.threeDose to 4), emptySet(), mapOf(family.fourDose to 3, EMPTY_VIAL to 1), 0.0,
            zeroValueByproducts = setOf(EMPTY_VIAL))

        /**
         * Persisted interruption guards prevent a partial round from buying its original inputs again after relog.
         *
         * @author lare96
         */
        class DecantPotionData : InventoryScriptData() {
            var familyId = -1
            var roundInProgress = false
            var interruptedRound = false

            override fun load(data: JsonObject) {
                super.load(data)
                familyId = data.get("family_id").asInt
                roundInProgress = data.get("round_in_progress")?.asBoolean ?: false
                interruptedRound = data.get("interrupted_round")?.asBoolean ?: false
            }

            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("family_id", familyId)
                data.addProperty("round_in_progress", roundInProgress)
                data.addProperty("interrupted_round", interruptedRound)
            }
        }
    }

    override val verifiedProductionWithdrawals = true
    private val materials = listOf(Item(family.threeDose, 4))
    private var roundInProgress = false
    private var interruptedRound = false

    constructor(bot: Bot, data: DecantPotionData) : this(bot,
        requireNotNull(PotionFamily.forId(data.familyId)).also { require(it.fourDose == data.familyId) },
        data.duration, data.zones) {
        restoreInventoryState(data)
        roundInProgress = data.roundInProgress
        interruptedRound = data.interruptedRound
    }

    private fun merchantGoal(): Boolean = productionPlan?.let {
        !it.training && it.personalUsePrices.isEmpty() && it.recipe == recipe(family)
    } == true && !BotTradeIntentions.hasPersonalPotionReserve(bot, family)

    override fun inventorySupplyInitAllowed(resumed: Boolean): Boolean =
        !roundInProgress && !interruptedRound && merchantGoal()

    override fun withdraw(): List<Item> {
        if (!merchantGoal() || interruptedRound || roundInProgress) {
            stop()
            return emptyList()
        }
        return materials
    }

    override fun bankWithdraw(): List<Item> {
        if (!merchantGoal() || interruptedRound || roundInProgress) {
            stop()
            return emptyList()
        }
        return bot.productionBatch(materials)
    }

    override suspend fun onInventoryBankRequested(): Boolean =
        requestProductionBank(bot.inventory.computeNonDynamicAmountForId(family.threeDose) >= 4)

    private fun roundAllowed(): Boolean = !isPaused() && !isTerminated() && bot.state == EntityState.ACTIVE &&
        isActionSafe() && merchantGoal() && remainingDuration() > Duration.ZERO &&
        (productionPlan?.deadline ?: 0L) > System.currentTimeMillis()

    override suspend fun onExecuteInZone(): Boolean {
        if (!productionReady(bot.inventory.computeNonDynamicAmountForId(family.threeDose) >= 4)) return true
        val plan = productionPlan
        if (plan == null || !roundAllowed() || !BotProductionPlanner.remainsUseful(bot, plan)) {
            stop()
            return false
        }
        val slots = bot.inventory.withIndex().filter { (_, item) ->
            item?.id == family.threeDose && item.amount == 1 && !item.isDynamic
        }.take(4).map { it.index }
        if (slots.size != 4) { stop(); return false }
        val before = (family.ids + EMPTY_VIAL).associateWith { bot.inventory.computeAmountForId(it) }
        var complete = false
        roundInProgress = true
        try {
            if (!BotPotionDecanting.combine(bot, slots[0], slots[1], ::roundAllowed) ||
                !BotPotionDecanting.combine(bot, slots[2], slots[3], ::roundAllowed)) return false
            val partials = slots.filter { bot.inventory[it]?.id == family.twoDose }
            if (partials.size != 2 ||
                !BotPotionDecanting.combine(bot, partials[0], partials[1], ::roundAllowed)) return false
            complete = bot.inventory.computeAmountForId(family.threeDose) == before.getValue(family.threeDose) - 4 &&
                bot.inventory.computeAmountForId(family.fourDose) == before.getValue(family.fourDose) + 3 &&
                bot.inventory.computeAmountForId(family.twoDose) == before.getValue(family.twoDose) &&
                bot.inventory.computeAmountForId(family.oneDose) == before.getValue(family.oneDose) &&
                bot.inventory.computeAmountForId(EMPTY_VIAL) == before.getValue(EMPTY_VIAL) + 1
            return complete
        } finally {
            if (complete) roundInProgress = false
            else {
                interruptedRound = true
                stop()
            }
        }
    }

    override fun onPaused() {
        if (roundInProgress) {
            interruptedRound = true
            stop()
        }
    }

    override fun snapshot(): DecantPotionData = DecantPotionData().also {
        it.familyId = family.fourDose
        it.roundInProgress = roundInProgress
        it.interruptedRound = interruptedRound
        saveInventoryState(it)
    }
}
