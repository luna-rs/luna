package game.bot.scripts.skills

import api.bot.action.trading.BotTradeIntentions
import api.bot.script.BotScript
import api.bot.script.InventoryBotScript
import api.predef.herblore
import api.predef.ext.*
import game.skill.herblore.grindIngredient.Ingredient
import game.skill.herblore.identifyHerb.Herb
import game.skill.herblore.makePotion.FinishedPotion
import game.skill.herblore.makeUnfPotion.UnfPotion
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Continues owned herbs toward finished potions without restarting gathering or replacing consumed herbs.
 * Handoffs retain the caller's remaining session budget and acquire only the missing secondary or water.
 *
 * @author lare96
 */
internal object HerbloreProduction {
    fun owned(bot: Bot, id: Int): Long = bot.inventory.computeAmountForId(id).toLong() +
        bot.bank.computeAmountForId(id)

    /**
     * Buys a bounded reserve matched to owned primary stock. Partial affordable purchases can still be used;
     * an unavailable shop, empty wallet or timeout never creates ingredients or requests replacement herbs.
     */
    suspend fun prepare(bot: Bot, primary: Int, consumable: Int): Boolean {
        if (!BotScript.ActionSafety.NON_COMBAT.allows(bot) || owned(bot, primary) < 1) return false
        if ((bot.scriptStack.current() as? InventoryBotScript)?.productionPlan != null)
            return owned(bot, consumable) > 0L
        // Shop acquisition targets carried stock. Leave one slot for currency instead of requesting
        // an impossible carried quantity of non-stackable ingredients.
        val quantity = minOf(owned(bot, primary), (bot.inventory.capacity() - 1).coerceAtLeast(1).toLong()).toInt()
        BotTradeIntentions.recordSupplies(bot, listOf(Item(consumable, minOf(quantity, 14))), stockTarget = 1_000)
        if (owned(bot, consumable) < 1) {
            bot.preferences.raiseWantedItemTarget(consumable, 1_000)
            withTimeoutOrNull(120.seconds) { bot.actionHandler.shop.obtainWantedItem(consumable, quantity) }
        }
        return BotScript.ActionSafety.NON_COMBAT.allows(bot) && owned(bot, primary) > 0 && owned(bot, consumable) > 0
    }

    fun afterIdentify(bot: Bot, herb: Herb, duration: Duration): BotScript? {
        if ((bot.scriptStack.current() as? InventoryBotScript)?.productionPlan != null) return null
        if (duration <= Duration.ZERO) return null
        val potion = UnfPotion.HERB_TO_UNF[herb.identified] ?: return null
        return MakeUnfPotionBotScript(bot, potion, duration).takeIf { it.isEligible() }
    }

    fun afterUnfinished(bot: Bot, potion: UnfPotion, duration: Duration): BotScript? =
        if ((bot.scriptStack.current() as? InventoryBotScript)?.productionPlan != null) null
        else finished(bot, duration) { it.unf == potion.id }

    fun afterGrinding(bot: Bot, ingredient: Ingredient, duration: Duration): BotScript? =
        if ((bot.scriptStack.current() as? InventoryBotScript)?.productionPlan != null) null
        else finished(bot, duration) { it.secondary == ingredient.newId }

    fun afterFinished(bot: Bot, potion: FinishedPotion, duration: Duration): BotScript? {
        if ((bot.scriptStack.current() as? InventoryBotScript)?.productionPlan != null) return null
        if (duration <= Duration.ZERO) return null
        // A completed batch may have used a small pre-existing secondary stock. Continue the remaining
        // primary stock, but a failed acquisition or production attempt cannot enter this handoff.
        if (owned(bot, potion.unf) > 0) return mixing(bot, potion, duration)
        val unfinished = UnfPotion.entries.firstOrNull { it.id == potion.unf } ?: return null
        MakeUnfPotionBotScript(bot, unfinished, duration).takeIf { it.isEligible() }?.let { return it }
        val herb = Herb.entries.firstOrNull { it.identified == unfinished.herb } ?: return null
        return IdentifyHerbBotScript(bot, herb, duration, allowGathering = false).takeIf { it.isEligible() }
    }

    private fun finished(bot: Bot, duration: Duration, matches: (FinishedPotion) -> Boolean): BotScript? {
        if (duration <= Duration.ZERO) return null
        val potion = FinishedPotion.entries.filter { matches(it) && bot.herblore.staticLevel >= it.level && owned(bot, it.unf) > 0 }
            .sortedWith(compareByDescending<FinishedPotion> { owned(bot, it.secondary) > 0 }.thenByDescending { it.exp })
            .firstOrNull() ?: return null
        return mixing(bot, potion, duration)
    }

    fun mixing(bot: Bot, potion: FinishedPotion, duration: Duration): InventoryBotScript? {
        if (duration <= Duration.ZERO || !MakePotionBotScript(bot, potion, duration).isEligible()) return null
        if (owned(bot, potion.secondary) == 0L) {
            if (potion == FinishedPotion.RANGING_POTION) {
                return TelegrabBotScript(bot, TelegrabBotScript.WINE, false, duration,
                    TelegrabBotScript.wineZones(bot), herbloreContinuation = true).takeIf { it.isEligible() }
            }
            Ingredient.entries.firstOrNull { it.newId == potion.secondary }?.let { ingredient ->
                GrindIngredientBotScript(bot, ingredient, duration).takeIf { it.isEligible() }?.let { return it }
            }
        }
        return MakePotionBotScript(bot, potion, duration)
    }
}
