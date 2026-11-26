package game.bot.scripts

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.script.DynamicBotScript
import api.predef.ext.get
import api.predef.ext.totalItemAmount
import game.content.quests.Quest
import game.content.quests.QuestGates
import game.npc.spawn.falador.InitiateArmoury
import io.luna.game.model.def.EquipmentDefinition
import io.luna.game.model.item.Item
import io.luna.game.model.item.shop.Shop
import io.luna.game.model.item.shop.ShopInterface
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.minutes

/**
 * Buys one of each missing, wearable Initiate piece through Sir Tiffy's native shop.
 *
 * @author lare96
 */
internal class BuyInitiateArmourBotScript private constructor(bot: Bot) : DynamicBotScript(bot) {

    companion object {
        private const val COINS = 995
        private var Bot.nextArmouryVisit by Attr.long()

        fun select(bot: Bot): BuyInitiateArmourBotScript? {
            if (System.currentTimeMillis() < bot.nextArmouryVisit || missingPieces(bot).isEmpty()) return null
            // TODO Recruitment Drive: queue the quest bot here when the optional gate is closed.
            if (!canBuy(bot)) return null
            val shop = bot.world.shops.lookup(InitiateArmoury.ARMOURY).orElse(null) ?: return null
            return BuyInitiateArmourBotScript(bot).takeIf {
                shopQuotes(bot, shop).isNotEmpty() && bot.actionHandler.shop.shopDistance(shop) != null
            }
        }

        private fun canBuy(bot: Bot) = ActionSafety.NON_COMBAT.allows(bot) &&
            QuestGates.passes(bot, Quest.RECRUITMENT_DRIVE)

        private fun missingPieces(bot: Bot): List<Int> {
            if (bot.preferences.isSkiller || bot.isTemporary) return emptyList()
            val build = bot.preferences.pk?.build()
            return InitiateArmoury.PIECES.filter { id ->
                val definition = EquipmentDefinition.ALL.get(id).orElse(null) ?: return@filter false
                bot.preferences.getWantedItem(id)?.target()?.let { it > 0 } == true &&
                    definition.meetsAllRequirements(bot) &&
                    (build == null || definition.requirements.all { build.targetLevel(it.id) >= it.level }) &&
                    bot.totalItemAmount(id) == 0
            }
        }

        private fun coins(bot: Bot): Long = bot.inventory.computeAmountForId(COINS).toLong() +
            bot.bank.computeAmountForId(COINS)

        private fun shopQuotes(bot: Bot, shop: Shop): Map<Int, Int> {
            val wallet = coins(bot)
            return missingPieces(bot).mapNotNull { id ->
                if (!shop.items.contains(id)) return@mapNotNull null
                shop.computeBuyValue(id, 1).orElse(-1).takeIf { it.toLong() in 1..wallet }?.let { id to it }
            }.toMap()
        }
    }

    override suspend fun run(): Boolean {
        if (!canBuy(bot)) return true
        bot.nextArmouryVisit = System.currentTimeMillis() + 10.minutes.inWholeMilliseconds
        withTimeoutOrNull(6.minutes) { buyFromArmoury() }
        return true
    }

    private suspend fun buyFromArmoury() {
        val shop = bot.world.shops.lookup(InitiateArmoury.ARMOURY).orElse(null) ?: return
        val quotes = shopQuotes(bot, shop)
        if (quotes.isEmpty() || handler.shop.shopDistance(shop) == null) return
        val wallet = minOf(coins(bot), quotes.values.sumOf { it.toLong() }, Int.MAX_VALUE.toLong()).toInt()
        val basket = quotes.keys.map { Item(it) } + Item(COINS, wallet)
        if (bot.inventory.computeAmountForId(COINS) < wallet || !bot.inventory.hasSpaceForAll(basket)) {
            if (!handler.banking.travelToBankOpen() || !canBuy(bot)) return
            if (!bot.inventory.hasSpaceForAll(basket) && !handler.banking.depositInventory(setOf(COINS))) return
            handler.banking.clickBankingMode(noted = false)
            val withdraw = wallet - bot.inventory.computeAmountForId(COINS)
            if (withdraw > 0 && !handler.banking.withdraw(Item(COINS, withdraw)) { canBuy(bot) }) return
        }
        val overlay = handler.shop.travelToShop(shop) { canBuy(bot) } ?: return
        try {
            for (id in missingPieces(bot)) {
                if (!canBuy(bot) || !handler.shop.canTradeWith(shop)) break
                val quote = quotes[id] ?: continue
                handler.shop.buyQuoted(shop, id, 1, quote)
            }
        } finally {
            if (bot.overlays[ShopInterface::class] === overlay) bot.overlays.closeWindows()
        }
    }
}
