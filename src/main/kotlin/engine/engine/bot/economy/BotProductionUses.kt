package engine.bot.economy

import api.bot.action.BotCombatActionHandler
import api.bot.script.AlchemyPolicy
import api.bot.script.ProductionEconomics
import api.predef.SKILL_MAGIC
import api.predef.magic
import game.bot.scripts.skills.AlchemyBotScript
import game.skill.magic.lowHighAlch.AlchemyType
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.def.ItemEconomyDefinition
import io.luna.game.model.item.Item
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.mob.Spellbook
import io.luna.game.model.mob.bot.Bot
import kotlin.math.ceil

/**
 * Values produced goods through current executable exits instead of assuming every guide is a sale price.
 * One decision examines accessible shops once for a bounded set of product ids. It never scans players,
 * creates sale tasks, or stores per-bot catalogues. Native sales and alchemy revalidate after travel.
 *
 * @author lare96
 */
object BotProductionUses {

    /**
     * Bounds the temporary valuation basket even when a caller presents a very large recipe catalogue.
     */
    internal const val MAX_PRODUCT_TYPES = 256

    /**
     * Returns conservative per-unit exits for at most one hundred native operations of each product.
     * Shop quotes include the whole prospective batch's stock discount. Recent actual market sales receive
     * a twenty-percent allowance for sale uncertainty. Alchemy counts only supported, profitable targets
     * after the currently usable spell's rune costs, using a conservative purchase premium for those runes.
     * Missing exits have zero recoverable value; they may still be worth producing for bounded training XP.
     */
    fun recoverableValues(bot: Bot, products: Map<Int, Int>,
                          operations: Int = ProductionEconomics.MAX_OPERATIONS): Map<Int, Long> {
        require(operations in 1..ProductionEconomics.MAX_OPERATIONS)
        val definitions = LinkedHashMap<Int, ItemDefinition>()
        val amounts = HashMap<Int, Int>()
        val aliases = LinkedHashMap<Int, Int>()
        var examined = 0
        for ((id, yield) in products) {
            if (examined++ >= MAX_PRODUCT_TYPES) break
            if (yield < 1 || definitions.size >= MAX_PRODUCT_TYPES) continue
            val original = ItemDefinition.ALL.get(id).orElse(null) ?: continue
            val source = original.unnotedId.orElse(id)
            // Native fermentation turns every unfermented jug into ordinary wine after its own delay.
            val base = if (source == 1995) 1993 else source
            val definition = ItemDefinition.ALL.get(base).orElse(null) ?: continue
            if (base == 995 || !definition.isTradeable) continue
            aliases[source] = base
            definitions[base] = definition
            val amount = (yield.toLong() * operations)
                .coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
            amounts[base] = maxOf(amounts[base] ?: 0, amount)
        }
        if (definitions.isEmpty()) return emptyMap()
        val values = HashMap<Int, Long>()
        for (id in definitions.keys) {
            val observed = bot.world.economy.getRecentTradeReference(id)?.price ?: 0.0
            values[id] = if (observed.isFinite() && observed > 0.0) (observed * 0.80).toLong() else 0L
        }

        // Merchant scripts do not execute ordinary shop liquidation; do not invent that exit for them.
        if (!bot.preferences.isMerchant) for (shop in bot.world.shops.values()) {
            if (shop.currency != Currency.COINS || !shop.botAccess.apply(bot)) continue
            for ((id, definition) in definitions) {
                val noted = definition.notedId.orElse(id)
                val saleId = if (noted != id && shop.computeCanSell(noted)) noted else id
                if (shop.buyPolicy == BuyPolicy.ALL && !definition.isStackable && noted != id && saleId == id) continue
                if (!shop.computeCanSell(saleId) || !shop.items.hasSpaceFor(Item(saleId, amounts.getValue(id)))) continue
                val unit = shop.computeSellValue(saleId, amounts.getValue(id)).toLong()
                    .coerceAtLeast(0L) / amounts.getValue(id)
                values[id] = maxOf(values.getValue(id), unit)
            }
        }

        val type = when {
            bot.spellbook != Spellbook.REGULAR || !BotCombatActionHandler.mayGainExperience(bot, SKILL_MAGIC) -> null
            bot.magic.staticLevel >= AlchemyType.HIGH.level -> AlchemyType.HIGH
            bot.magic.staticLevel >= AlchemyType.LOW.level -> AlchemyType.LOW
            else -> null
        }
        if (type != null) {
            val staff = AlchemyPolicy.resolveFireStaff(bot)
            val runeCosts = AlchemyPolicy.runeCosts(bot, type, staff)
            val ownedRunes = runeCosts.associate { rune -> rune.id to
                (bot.inventory.computeNonDynamicAmountForId(rune.id).toLong() +
                    bot.bank.computeNonDynamicAmountForId(rune.id)) }
            var runeCost = 0L
            for (rune in runeCosts) {
                val unit = guide(bot, rune.id).coerceAtMost(Long.MAX_VALUE / 2) * 2
                val cost = if (unit > Long.MAX_VALUE / rune.amount) Long.MAX_VALUE else unit * rune.amount
                runeCost = if (runeCost > Long.MAX_VALUE - cost) Long.MAX_VALUE else runeCost + cost
            }
            for ((id, definition) in definitions) {
                if (!AlchemyBotScript.supportsLiquidation(id)) continue
                // A possible future rune seller is insufficient evidence of an executable disposal batch.
                if (runeCosts.any { rune ->
                    ownedRunes.getValue(rune.id) < rune.amount.toLong() * amounts.getValue(id)
                }) continue
                val proceeds = (definition.value * type.valueMultiplier).toInt().coerceAtLeast(1).toLong()
                val net = proceeds - runeCost
                if (net > guide(bot, id)) values[id] = maxOf(values.getValue(id), net)
            }
        }
        return aliases.mapValues { (_, base) -> values.getValue(base) }
    }

    private fun guide(bot: Bot, id: Int): Long {
        val observed = bot.world.economy.prices[id]?.price
        if (observed != null && observed.isFinite() && observed > 0.0) return ceil(observed).toLong().coerceAtLeast(1)
        return (ItemEconomyDefinition.ALL.get(id).orElse(null)?.initialPrice?.toLong()
            ?: ItemDefinition.ALL.retrieve(id).value.toLong()).coerceAtLeast(1)
    }
}
